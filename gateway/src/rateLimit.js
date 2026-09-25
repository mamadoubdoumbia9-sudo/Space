/**
 * Limitation de débit locale — deuxième barrière après celle du serveur.
 *
 * Les plafonds appliqués sont ceux de WhatsApp et ceux du projet :
 *   - 10 actions par minute (toutes actions confondues) ;
 *   - 5 signalements par heure ;
 *   - 20 signalements par jour.
 *
 * Ces compteurs ne sont PAS désactivables : si vous les augmentez, vous vous
 * exposez à une restriction de votre compte WhatsApp. Aucun mécanisme de
 * contournement (changement d'adresse, rotation de session, etc.) n'est fourni.
 */
export class SlidingWindowLimiter {
  constructor({ perMinute = 10, reportsPerHour = 5, reportsPerDay = 20 } = {}) {
    this.perMinute = perMinute;
    this.reportsPerHour = reportsPerHour;
    this.reportsPerDay = reportsPerDay;
    this.events = [];
    this.reports = [];
  }

  #prune(now) {
    this.events = this.events.filter((t) => now - t < 60_000);
    this.reports = this.reports.filter((t) => now - t < 86_400_000);
  }

  check(kind = 'action') {
    const now = Date.now();
    this.#prune(now);
    if (this.events.length >= this.perMinute) {
      const retry = Math.ceil((60_000 - (now - this.events[0])) / 1000);
      return { allowed: false, reason: `Limite de ${this.perMinute} actions par minute atteinte.`, retry_after: retry };
    }
    if (kind === 'report') {
      const lastHour = this.reports.filter((t) => now - t < 3_600_000);
      if (lastHour.length >= this.reportsPerHour) {
        const retry = Math.ceil((3_600_000 - (now - lastHour[0])) / 1000);
        return { allowed: false, reason: `Limite de ${this.reportsPerHour} signalements par heure atteinte.`, retry_after: retry };
      }
      if (this.reports.length >= this.reportsPerDay) {
        const retry = Math.ceil((86_400_000 - (now - this.reports[0])) / 1000);
        return { allowed: false, reason: `Limite de ${this.reportsPerDay} signalements par jour atteinte.`, retry_after: retry };
      }
    }
    return { allowed: true, retry_after: 0 };
  }

  record(kind = 'action') {
    const now = Date.now();
    this.events.push(now);
    if (kind === 'report') this.reports.push(now);
  }

  snapshot() {
    const now = Date.now();
    this.#prune(now);
    return {
      actions_last_minute: this.events.length,
      reports_last_hour: this.reports.filter((t) => now - t < 3_600_000).length,
      reports_last_day: this.reports.length,
      per_minute: this.perMinute,
      reports_per_hour: this.reportsPerHour,
      reports_per_day: this.reportsPerDay,
    };
  }
}
