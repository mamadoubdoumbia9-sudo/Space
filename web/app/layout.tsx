import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "SignalPro — signalement de comptes WhatsApp malveillants",
  description:
    "Signaler des arnaques, du spam et du harcèlement WhatsApp avec preuves, suivre l'état réel " +
    "des signalements, et protéger la communauté. SignalPro ne peut pas suspendre un compte : " +
    "seul Meta examine et décide.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
