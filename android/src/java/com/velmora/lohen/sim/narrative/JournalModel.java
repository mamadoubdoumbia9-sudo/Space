/*
 * LOHEN — sim/narrative/JournalModel.java
 *
 * 14.04 LE JOURNAL (menu, 5 onglets)
 *  - LETTRES : les 7 emplacements (6 vides au Chapitre 1), et les 6 lettres
 *    livrees. Chaque lettre est lisible integralement, avec le shader
 *    `letter_paper`, zoomable, et en VO.
 *  - ECHOS : les 31 vignettes avec leur illustration auto-capturee.
 *  - GENS : 17 fiches de personnages, ecrites de la main de Lohen, avec des
 *    ratures. Elles se completent au fil du jeu. Deux d'entre elles
 *    contiennent des choses qu'il n'a dites a personne.
 *  - OBJETS : les 11 objets tombes des Figures. Sans explication.
 *  - CARNET : les notes de Lohen, ecrites en fin de sequence. 8 entrees
 *    manuscrites (police manuscrite dediee, 14.06).
 *
 * 10.14 : le journal de Lohen a une page avec SEPT emplacements. Six sont
 * vides. Le joueur le voit des la premiere heure. C'est une promesse.
 * 08.12 V7 — DONNER : il y a 6 lettres livrables au Chapitre 1 (en plus de
 * la derniere). Chaque livraison declenche une scene courte, jouable,
 * unique. Ce n'est pas une quete secondaire : c'est l'identite du
 * personnage rendue jouable.
 * 11.07 : chaque Echo lu s'ajoute au journal avec une illustration (un plan
 * fixe du moment cle, capture automatiquement, cadre par un
 * `EchoPortraitCamera` place a la main). 31 illustrations. Elles forment,
 * mises cote a cote, l'histoire.
 * 09.24 [OBL] : AUCUN compteur de collectibles n'est affiche en jeu. Le
 * journal les liste apres coup. Pas de « 7/31 » a l'ecran. Jamais.
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.GameState;
import com.velmora.lohen.sim.core.Localization;
import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JournalModel implements EventBus.Listener {

    /* ---------------- onglets ---------------- */
    public static final int TAB_LETTERS = 0;
    public static final int TAB_ECHOS = 1;
    public static final int TAB_PEOPLE = 2;
    public static final int TAB_OBJECTS = 3;
    public static final int TAB_NOTEBOOK = 4;
    public static final int TAB_COUNT = 5;

    public static final String[] TAB_KEYS = {
            "journal.tab.letters", "journal.tab.echos", "journal.tab.people",
            "journal.tab.objects", "journal.tab.notebook",
    };

    /* ---------------- comptes imposes par la spec ---------------- */
    public static final int LETTER_SLOTS = 7;              /* 10.14 / 14.04 */
    public static final int LETTER_SLOTS_EMPTY_CH1 = 6;
    public static final int DELIVERABLE_LETTERS = 6;       /* 08.12 */
    public static final int LETTER_FRAGMENTS = 9;          /* 09.24 */
    public static final int ECHO_VIGNETTES = 31;           /* 11.07 */
    public static final int PEOPLE_FILES = 17;             /* 14.04 */
    public static final int PEOPLE_FILES_WITH_SECRETS = 2;
    public static final int FIGURE_OBJECTS = 11;           /* 10.03 */
    public static final int NOTEBOOK_ENTRIES = 8;          /* 14.04, une par sequence */

    /** 09.24 [OBL] : jamais de compteur a l'ecran. */
    public static final boolean SHOW_COUNTERS = false;

    /* ------------------------------------------------------------------ */
    /* Structures                                                          */
    /* ------------------------------------------------------------------ */

    /** Un des 7 emplacements de la page des lettres (10.14). */
    public static final class LetterSlot {
        public final int index;
        public String title = "";
        public String author = "";
        public String body = "";
        public boolean filled;
        public String font = "rouge_script";     /* 14.06 : l'ecriture d'Esteban */
        public String sequence = "";

        LetterSlot(int index) {
            this.index = index;
        }
    }

    /** Une des 6 lettres que Lohen porte et remet (08.12 V7 — DONNER). */
    public static final class Delivery {
        public final String id;
        public final String recipient;
        public final String sequence;
        public final String scene;         /* scene courte, jouable, unique */
        public final String note;
        public boolean delivered;
        public float deliveredAt = -1f;

        Delivery(String id, String recipient, String sequence, String scene, String note) {
            this.id = id;
            this.recipient = recipient;
            this.sequence = sequence;
            this.scene = scene;
            this.note = note;
        }
    }

    /** Une vignette d'Echo : texte + illustration auto-capturee (11.07). */
    public static final class EchoVignette {
        public final String echoId;
        public String object = "";
        public String sequence = "";
        public boolean read;
        public boolean portraitCaptured;
        public String portraitId = "";
        public float capturedAt = -1f;

        EchoVignette(String echoId) {
            this.echoId = echoId;
        }
    }

    /** Une fiche de personnage, ecrite de la main de Lohen, avec des ratures. */
    public static final class PeopleFile {
        public final String id;
        public final String name;
        public final String role;
        public final String sequence;
        public final String[] facts;
        public final boolean secret;       /* 14.04 : deux fiches */
        public int factsRevealed;
        public int ratures;
        public float lastTouched = -1f;

        PeopleFile(String id, String name, String role, String sequence,
                   String[] facts, boolean secret) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.sequence = sequence;
            this.facts = facts;
            this.secret = secret;
        }

        public float completion() {
            return facts.length == 0 ? 0f : factsRevealed / (float) facts.length;
        }
    }

    /** Un objet tombe d'une Figure (10.03). Sans explication. */
    public static final class FigureObject {
        public final String id;
        public String name;
        public boolean held;
        public String sequence = "";

        FigureObject(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Une entree du carnet, manuscrite, ecrite en fin de sequence. */
    public static final class NotebookEntry {
        public final String sequence;
        public final String title;
        public final String text;
        public boolean written;
        public float writtenAt = -1f;

        NotebookEntry(String sequence, String title, String text) {
            this.sequence = sequence;
            this.title = title;
            this.text = text;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Les 6 lettres livrables (08.12)                                     */
    /* ------------------------------------------------------------------ */

    public static final Map<String, Delivery> DELIVERIES = new LinkedHashMap<String, Delivery>();

    static {
        add(new Delivery("DL1", "Brel, le forgeron du marche", "S3", "S3_D14",
                "une facture de cloche, impayee depuis trois ans"));
        add(new Delivery("DL2", "Odile, laveuse", "S3", "S3_D18",
                "une lettre de son frere, parti avant la Maree"));
        add(new Delivery("DL3", "Wenna, cordiere", "S3", "S3_D22",
                "un faire-part que personne n'a voulu signer"));
        add(new Delivery("DL4", "Niels, employe du Registre", "S3", "S3_D27",
                "un bordereau : 641 noms, une seule signature"));
        add(new Delivery("DL5", "Thea, dans les conduits", "S5", "S5_D09",
                "une lettre qu'elle a ecrite elle-meme et qu'elle ne peut pas porter"));
        add(new Delivery("DL6", "Garric, au poste de milice", "S7", "S7_D12",
                "un ordre de mission annule le 14 octobre a 23 h 40"));
    }

    private static void add(Delivery d) {
        DELIVERIES.put(d.id, d);
    }

    /* ------------------------------------------------------------------ */
    /* Les 17 fiches (14.04)                                               */
    /* ------------------------------------------------------------------ */

    public static final Map<String, PeopleFile> PEOPLE = new LinkedHashMap<String, PeopleFile>();

    static {
        /* Deux fiches contiennent des choses qu'il n'a dites a personne :
         * celle d'Esteban, et celle de la fille du Verrier. */
        add(new PeopleFile("lohen", "Lohen", "relayeur, matricule 0114, 27 ans", "S1",
                new String[]{
                        "Il a appris a lire tard. Esteban lui a appris correctement.",
                        "Il garde les lettres non remises. Toutes.",
                        "Il a pose la moitie des anneaux du marche.",
                }, false));
        add(new PeopleFile("esteban", "Esteban", "accordeur de cloches, copiste, 31 ans", "S2",
                new String[]{
                        "Il dansait mal. Il riait.",
                        "Il accordait les cloches au bronze, pas au diapason.",
                        "Il a ecrit 314 fois le meme nom sur des feuilles.",
                        "Je n'ai pas repondu a la derniere dispute.",
                }, true));
        add(new PeopleFile("sol", "Sol", "13 ans, voleuse de sacoche", "S3",
                new String[]{
                        "Iel a vu le matricule 0114 et s'est arrete.",
                        "Iel a survecu a la Maree. Quatre phrases, sans emotion.",
                        "Iel dort contre la porte du Phare.",
                }, false));
        add(new PeopleFile("mireille", "Mireille Vandeck", "gardienne de la Bibliotheque, 58 ans", "S4",
                new String[]{
                        "Elle ment une fois sur trois conversations.",
                        "Elle donne une cle et une direction.",
                        "Sa phrase, je ne la comprendrai qu'en haut.",
                }, false));
        add(new PeopleFile("tallec", "Capitaine Orval Tallec", "milice, 44 ans", "S3",
                new String[]{
                        "Il a perdu 31 hommes le 14 octobre.",
                        "Il ne veut pas m'aider. Il m'aide.",
                        "Il donne un harnais. Il dit : « Ton grand roux. »",
                }, false));
        add(new PeopleFile("anselme", "Anselme Roux", "maitre verrier, 52 ans", "S7",
                new String[]{
                        "Il a vide le dossier du Registre.",
                        "Il a lu le meme Echo 4 000 fois.",
                        "Il ne peut plus parler. Il montre.",
                        "Il s'est laisse durcir. Je suis parti.",
                }, false));
        add(new PeopleFile("fille_verrier", "La fille du Verrier", "—", "S7",
                new String[]{
                        "Elle tourne la tete. Elle me regarde.",
                        "Personne ne sait son nom. Moi non plus.",
                }, true));
        add(new PeopleFile("pallas", "Pallas", "quais bas", "S1",
                new String[]{"Il parle de loin. Il ne s'approche jamais du verre."}, false));
        add(new PeopleFile("brel", "Brel", "forgeron du marche suspendu", "S3",
                new String[]{"Il frappe hors champ. On l'entend avant de le voir."}, false));
        add(new PeopleFile("odile", "Odile", "laveuse", "S3",
                new String[]{"Elle lave le meme linge tous les jours."}, false));
        add(new PeopleFile("wenna", "Wenna", "cordiere", "S3",
                new String[]{"Elle a tresse la corde qui a tenu le monte-charge."}, false));
        add(new PeopleFile("niels", "Niels", "employe du Registre", "S3",
                new String[]{"Il a signe 641 radiations d'une seule main."}, false));
        add(new PeopleFile("garric", "Garric", "milice, poste bas", "S7",
                new String[]{"Il a garde un ordre annule pendant trois ans."}, false));
        add(new PeopleFile("thea", "Thea", "conduits, dans le noir", "S5",
                new String[]{"Elle parle dans un tube. On ne voit jamais sa bouche."}, false));
        add(new PeopleFile("hanno", "Hanno", "vieux des passerelles", "S3",
                new String[]{"Il repeint la meme porte tous les jours."}, false));
        add(new PeopleFile("vane", "Vane", "patron de peche", "S2",
                new String[]{"Son bateau est a l'envers. Il le dit a l'endroit."}, false));
        add(new PeopleFile("caro", "Caro", "marchande du marche", "S3",
                new String[]{"Elle vend des choses qu'elle ne possede plus."}, false));
    }

    private static void add(PeopleFile f) {
        PEOPLE.put(f.id, f);
    }

    /* ------------------------------------------------------------------ */
    /* Les 8 entrees du carnet (14.04)                                     */
    /* ------------------------------------------------------------------ */

    public static final List<NotebookEntry> NOTEBOOK = new ArrayList<NotebookEntry>(8);

    static {
        NOTEBOOK.add(new NotebookEntry("S1", "Quais bas",
                "Quarante bottes sur le muret. Alignees par taille. Quelqu'un a pris "
                        + "le temps. Je n'ai pas la lettre de ce quelqu'un. Je l'aurai."));
        NOTEBOOK.add(new NotebookEntry("S2", "Le verre",
                "On marche dessus. C'est tiede. Sous mes pieds il y avait un tramway, "
                        + "des gens assis. J'ai marche. Je l'ecris pour ne pas l'oublier."));
        NOTEBOOK.add(new NotebookEntry("S3", "Le marche",
                "Six lettres remises. La ville a continue, mal. Tallec a dit "
                        + "« Ton grand roux. » Personne d'autre ne le dit jamais."));
        NOTEBOOK.add(new NotebookEntry("S4", "La Bibliotheque",
                "Le dossier existe. Il est vide. La poussiere autour est propre : "
                        + "quelqu'un est venu recemment. Mireille ment une fois sur trois."));
        NOTEBOOK.add(new NotebookEntry("S5", "Les conduits",
                "Sol a raconte la Maree en quatre phrases. Sans emotion. J'ai pose la "
                        + "lanterne trois fois. Huit metres dans le noir, a chaque fois."));
        NOTEBOOK.add(new NotebookEntry("S6", "La salle de bal",
                "J'y suis retourne. Deux ans avant. Il dansait mal. Je n'ai pas dit "
                        + "la phrase que j'aurais du dire, et je l'ai sue apres."));
        NOTEBOOK.add(new NotebookEntry("S7", "La descente",
                "Roux a vide le dossier. Il a lu le meme Echo quatre mille fois. Il "
                        + "s'est assis. Je suis parti. Je ne sais pas lequel des deux "
                        + "etait le plus dur."));
        NOTEBOOK.add(new NotebookEntry("S8", "Le Phare",
                "Trois cent quatorze feuilles avec mon matricule. Une lampe allumee, "
                        + "une couverture pliee, un lit froid depuis plusieurs jours. "
                        + "Il reste six lettres."));
    }

    /* ------------------------------------------------------------------ */
    /* Etat                                                                */
    /* ------------------------------------------------------------------ */

    private final EventBus bus;
    private final ContentDb db;
    private final GameState state;
    private final Localization loc;

    private final LetterSlot[] slots = new LetterSlot[LETTER_SLOTS];
    private final Map<String, EchoVignette> vignettes = new LinkedHashMap<String, EchoVignette>();
    private final Map<String, FigureObject> objects = new LinkedHashMap<String, FigureObject>();
    private final List<String> fragments = new ArrayList<String>(LETTER_FRAGMENTS);

    private int tab = TAB_LETTERS;
    private boolean open;
    private float openAnim;          /* 14.07 : ease_out_quint, 180 ms */
    private int entryIndex;          /* entree survolee dans l'onglet courant */
    private float zoom = 1f;         /* lettres zoomables (14.04) */
    private String viewedLetter = "";
    private float playTime;
    private int deliveriesMade;
    private int portraitsRequested;
    private int notebookWritten;
    private int fragmentCount;

    public JournalModel(EventBus bus, ContentDb db, GameState state, Localization loc) {
        this.bus = bus;
        this.db = db;
        this.state = state;
        this.loc = loc;
        for (int i = 0; i < LETTER_SLOTS; i++) {
            slots[i] = new LetterSlot(i);
            if (i < LETTER_SLOTS - 1) {
                slots[i].title = "";       /* 10.14 : six emplacements VIDES */
            }
        }
        buildVignettes();
        buildObjects();
        if (bus != null) {
            bus.connect(EventBus.ECHO_FINISHED, this);
            bus.connect(EventBus.LETTER_FOUND, this);
            bus.connect(EventBus.FIGURE_DROPPED_OBJECT, this);
            bus.connect(EventBus.SEQUENCE_CHANGED, this);
            bus.connect(EventBus.LETTER_DELIVERED, this);
            bus.connect(EventBus.SAVE_LOADED, this);
        }
    }

    private void buildVignettes() {
        List<ContentDb.EchoRecord> echos = db == null ? null : db.echos();
        if (echos != null) {
            for (ContentDb.EchoRecord e : echos) {
                EchoVignette v = new EchoVignette(e.id);
                v.object = e.object;
                v.sequence = e.seq;
                vignettes.put(e.id, v);
            }
        }
        /* garde : les 31 doivent exister meme sans base de contenu */
        for (int i = vignettes.size() + 1; i <= ECHO_VIGNETTES; i++) {
            String id = "E" + (i < 10 ? "0" + i : String.valueOf(i));
            if (!vignettes.containsKey(id)) {
                vignettes.put(id, new EchoVignette(id));
            }
        }
    }

    private void buildObjects() {
        for (int i = 0; i < FIGURE_OBJECTS; i++) {
            objects.put("OBJ-" + (i + 1), new FigureObject("OBJ-" + (i + 1), ""));
        }
    }

    /* ------------------------------------------------------------------ */
    /* EventBus                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void onEvent(String signal, Object[] args) {
        if (EventBus.ECHO_FINISHED.equals(signal)) {
            String id = str(args, 0);
            markEchoRead(id);
        } else if (EventBus.FIGURE_DROPPED_OBJECT.equals(signal)) {
            String item = str(args, 0);
            addObject(item, str(args, 1));
        } else if (EventBus.LETTER_FOUND.equals(signal)) {
            addFragment(str(args, 0));
        } else if (EventBus.LETTER_DELIVERED.equals(signal)) {
            markDelivered(str(args, 0));
        } else if (EventBus.SEQUENCE_CHANGED.equals(signal)) {
            String seq = str(args, 0);
            writeNotebookFor(previousOf(seq));
        } else if (EventBus.SAVE_LOADED.equals(signal)) {
            restoreFromState();
        }
    }

    private static String str(Object[] args, int i) {
        return args != null && i < args.length && args[i] != null ? String.valueOf(args[i]) : "";
    }

    private static String previousOf(String seq) {
        int n = 0;
        try {
            n = Integer.parseInt(seq.substring(1));
        } catch (Exception e) {
            return "";
        }
        return n <= 1 ? "" : "S" + (n - 1);
    }

    /* ------------------------------------------------------------------ */
    /* LETTRES                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * 19.07 / 10.17 : la lettre d'Esteban remplit le SEPTIEME emplacement.
     * Les six autres restent vides — c'est la promesse (10.14).
     */
    public boolean fillFinalLetter(String title, String body) {
        LetterSlot s = slots[LETTER_SLOTS - 1];
        s.title = title;
        s.body = body;
        s.author = "Esteban";
        s.filled = true;
        s.sequence = "S8";
        s.font = "rouge_script";
        if (state != null) {
            state.markLetterDelivered("LETTRE_ESTEBAN");
        }
        if (bus != null) {
            bus.emit(EventBus.JOURNAL_UPDATED, "letters", 1);
        }
        return true;
    }

    public int filledSlots() {
        int n = 0;
        for (LetterSlot s : slots) {
            if (s.filled) {
                n++;
            }
        }
        return n;
    }

    public LetterSlot slot(int i) {
        return slots[Maths.clamp(i, 0, LETTER_SLOTS - 1)];
    }

    /** 08.12 V7 — DONNER : remise d'une lettre, scene courte jouable unique. */
    public boolean deliver(String deliveryId, String npcId) {
        Delivery d = DELIVERIES.get(deliveryId);
        if (d == null || d.delivered) {
            return false;
        }
        d.delivered = true;
        d.deliveredAt = playTime;
        deliveriesMade++;
        if (state != null) {
            state.markLetterDelivered(deliveryId);
            state.setFlag("delivery." + deliveryId, npcId == null ? "" : npcId);
        }
        if (bus != null) {
            bus.emit(EventBus.LETTER_DELIVERY_SCENE, deliveryId, d.recipient, d.scene);
            bus.emit(EventBus.SCENE_STARTED, d.scene);
            bus.emit(EventBus.JOURNAL_UPDATED, "letters", deliveriesMade);
        }
        return true;
    }

    public void markDelivered(String deliveryId) {
        Delivery d = DELIVERIES.get(deliveryId);
        if (d != null && !d.delivered) {
            d.delivered = true;
            d.deliveredAt = playTime;
            deliveriesMade++;
        }
    }

    public List<Delivery> deliveries() {
        return new ArrayList<Delivery>(DELIVERIES.values());
    }

    public int deliveriesMade() {
        return deliveriesMade;
    }

    /** 09.24 : 9 fragments de lettre (lore d'autres habitants). */
    public boolean addFragment(String fragmentId) {
        if (fragmentId == null || fragmentId.isEmpty() || fragments.contains(fragmentId)) {
            return false;
        }
        fragments.add(fragmentId);
        fragmentCount = fragments.size();
        if (bus != null) {
            bus.emit(EventBus.JOURNAL_UPDATED, "fragments", fragmentCount);
        }
        return true;
    }

    public List<String> fragments() {
        return new ArrayList<String>(fragments);
    }

    /* ------------------------------------------------------------------ */
    /* ECHOS                                                               */
    /* ------------------------------------------------------------------ */

    public boolean markEchoRead(String echoId) {
        EchoVignette v = vignettes.get(echoId);
        if (v == null) {
            return false;
        }
        boolean first = !v.read;
        v.read = true;
        if (state != null) {
            state.markEchoRead(echoId);
        }
        if (first) {
            requestPortrait(echoId);
        }
        return first;
    }

    /**
     * 11.07 : l'illustration est un plan fixe du moment cle, capture par le
     * moteur, cadre par un `EchoPortraitCamera` place a la main.
     */
    public boolean requestPortrait(String echoId) {
        EchoVignette v = vignettes.get(echoId);
        if (v == null || v.portraitCaptured) {
            return false;
        }
        portraitsRequested++;
        if (bus != null) {
            bus.emit(EventBus.ECHO_PORTRAIT_REQUEST, echoId, portraitsRequested);
        }
        return true;
    }

    /** Le moteur rappelle cette methode quand la capture est terminee. */
    public void onPortraitCaptured(String echoId, String portraitId) {
        EchoVignette v = vignettes.get(echoId);
        if (v == null) {
            return;
        }
        v.portraitCaptured = true;
        v.portraitId = portraitId;
        v.capturedAt = playTime;
        if (bus != null) {
            bus.emit(EventBus.JOURNAL_UPDATED, "echos", echosRead());
        }
    }

    public int echosRead() {
        int n = 0;
        for (EchoVignette v : vignettes.values()) {
            if (v.read) {
                n++;
            }
        }
        return n;
    }

    public int portraitsCaptured() {
        int n = 0;
        for (EchoVignette v : vignettes.values()) {
            if (v.portraitCaptured) {
                n++;
            }
        }
        return n;
    }

    public EchoVignette vignette(String echoId) {
        return vignettes.get(echoId);
    }

    public List<EchoVignette> vignettes() {
        return new ArrayList<EchoVignette>(vignettes.values());
    }

    /* ------------------------------------------------------------------ */
    /* GENS                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Une fiche se complete au fil du jeu. `count` faits sont reveles et
     * `ratures` ratures sont ajoutees — Lohen ecrit, puis barre.
     */
    public boolean touchPerson(String personId, int revealCount, int ratures) {
        PeopleFile f = PEOPLE.get(personId);
        if (f == null) {
            return false;
        }
        int before = f.factsRevealed;
        f.factsRevealed = Maths.clamp(f.factsRevealed + revealCount, 0, f.facts.length);
        f.ratures += Math.max(0, ratures);
        f.lastTouched = playTime;
        if (f.factsRevealed != before && bus != null) {
            bus.emit(EventBus.JOURNAL_UPDATED, "gens", personId, f.factsRevealed);
        }
        return f.factsRevealed != before;
    }

    public PeopleFile person(String id) {
        return PEOPLE.get(id);
    }

    public List<PeopleFile> people() {
        return new ArrayList<PeopleFile>(PEOPLE.values());
    }

    public int peopleFilesTouched() {
        int n = 0;
        for (PeopleFile f : PEOPLE.values()) {
            if (f.factsRevealed > 0) {
                n++;
            }
        }
        return n;
    }

    public int secretFiles() {
        int n = 0;
        for (PeopleFile f : PEOPLE.values()) {
            if (f.secret) {
                n++;
            }
        }
        return n;
    }

    /* ------------------------------------------------------------------ */
    /* OBJETS                                                              */
    /* ------------------------------------------------------------------ */

    /** 10.03 : un objet tombe. Le journal l'enregistre. Sans explication. */
    public boolean addObject(String itemName, String sequence) {
        FigureObject free = null;
        for (FigureObject o : objects.values()) {
            if (!o.held) {
                free = o;
                break;
            }
        }
        if (free == null) {
            return false;
        }
        free.held = true;
        free.name = itemName;
        free.sequence = sequence;
        if (state != null) {
            state.addObjectFromFigure(free.id);
        }
        if (bus != null) {
            bus.emit(EventBus.JOURNAL_UPDATED, "objets", objectsHeld());
        }
        return true;
    }

    public int objectsHeld() {
        int n = 0;
        for (FigureObject o : objects.values()) {
            if (o.held) {
                n++;
            }
        }
        return n;
    }

    public List<FigureObject> objects() {
        return new ArrayList<FigureObject>(objects.values());
    }

    /* ------------------------------------------------------------------ */
    /* CARNET                                                              */
    /* ------------------------------------------------------------------ */

    /** 14.04 : les notes sont ecrites en FIN de sequence. */
    public boolean writeNotebookFor(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return false;
        }
        for (NotebookEntry e : NOTEBOOK) {
            if (e.sequence.equals(sequence) && !e.written) {
                e.written = true;
                e.writtenAt = playTime;
                notebookWritten++;
                if (state != null) {
                    state.addJournalNote(sequence + " : " + e.title);
                }
                if (bus != null) {
                    bus.emit(EventBus.JOURNAL_UPDATED, "carnet", notebookWritten);
                }
                return true;
            }
        }
        return false;
    }

    public int notebookWritten() {
        return notebookWritten;
    }

    public List<NotebookEntry> notebook() {
        return NOTEBOOK;
    }

    /* ------------------------------------------------------------------ */
    /* Navigation dans le journal                                          */
    /* ------------------------------------------------------------------ */

    public void open(int tab) {
        open = true;
        setTab(tab);
        if (bus != null) {
            bus.emit(EventBus.JOURNAL_OPENED, tab);
        }
    }

    public void close() {
        open = false;
        if (bus != null) {
            bus.emit(EventBus.JOURNAL_CLOSED);
        }
    }

    public void toggle() {
        if (open) {
            close();
        } else {
            open(TAB_LETTERS);
        }
    }

    public void setTab(int t) {
        tab = Maths.clamp(t, 0, TAB_COUNT - 1);
        entryIndex = 0;
        if (bus != null) {
            bus.emit(EventBus.MENU_CHANGED, "journal", tabName(tab));
        }
    }

    public static String tabName(int t) {
        switch (t) {
            case TAB_LETTERS:
                return "LETTRES";
            case TAB_ECHOS:
                return "ECHOS";
            case TAB_PEOPLE:
                return "GENS";
            case TAB_OBJECTS:
                return "OBJETS";
            case TAB_NOTEBOOK:
                return "CARNET";
            default:
                return "";
        }
    }

    public String tabLabel(int t) {
        return loc == null ? tabName(t) : loc.t(TAB_KEYS[Maths.clamp(t, 0, TAB_COUNT - 1)]);
    }

    public void moveEntry(int delta) {
        int max = entryCount(tab) - 1;
        entryIndex = Maths.clamp(entryIndex + delta, 0, Math.max(0, max));
    }

    public int entryCount(int t) {
        switch (t) {
            case TAB_LETTERS:
                return LETTER_SLOTS;
            case TAB_ECHOS:
                return vignettes.size();
            case TAB_PEOPLE:
                return PEOPLE.size();
            case TAB_OBJECTS:
                return objects.size();
            case TAB_NOTEBOOK:
                return NOTEBOOK.size();
            default:
                return 0;
        }
    }

    /** 14.04 : chaque lettre est lisible integralement et ZOOMABLE. */
    public void setZoom(float z) {
        zoom = Maths.clamp(z, 1f, 3f);
    }

    public float zoom() {
        return zoom;
    }

    public String viewedLetter() {
        return viewedLetter;
    }

    public void viewLetter(String slotOrDeliveryId) {
        viewedLetter = slotOrDeliveryId == null ? "" : slotOrDeliveryId;
    }

    public boolean open() {
        return open;
    }

    public int tab() {
        return tab;
    }

    public int entryIndex() {
        return entryIndex;
    }

    /** 14.07 : tout est en ease_out_quint, 180 ms. */
    public float openProgress() {
        return Maths.easeOutQuint(openAnim);
    }

    public void update(float dt) {
        playTime += dt;
        float target = open ? 1f : 0f;
        openAnim = Maths.moveTowards(openAnim, target, dt / 0.18f);
    }

    /* ------------------------------------------------------------------ */
    /* Sauvegarde                                                          */
    /* ------------------------------------------------------------------ */

    public void restoreFromState() {
        if (state == null) {
            return;
        }
        for (String id : state.echosRead()) {
            EchoVignette v = vignettes.get(id);
            if (v != null) {
                v.read = true;
            }
        }
        for (String id : state.objectsFromFigures()) {
            FigureObject o = objects.get(id);
            if (o != null) {
                o.held = true;
            }
        }
        for (String id : state.lettersDelivered()) {
            if ("LETTRE_ESTEBAN".equals(id)) {
                slots[LETTER_SLOTS - 1].filled = true;
            } else {
                markDelivered(id);
            }
        }
        List<String> notes = state.journalNotes();
        for (String n : notes) {
            int i = n.indexOf(':');
            if (i > 0) {
                writeNotebookFor(n.substring(0, i).trim());
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Garde CI                                                            */
    /* ------------------------------------------------------------------ */

    /** BLOC 22 : le journal a 5 onglets et les comptes exacts de la spec. */
    public static boolean specCompliant() {
        return TAB_COUNT == 5
                && LETTER_SLOTS == 7
                && LETTER_SLOTS_EMPTY_CH1 == 6
                && DELIVERIES.size() == DELIVERABLE_LETTERS
                && ECHO_VIGNETTES == 31
                && PEOPLE.size() == PEOPLE_FILES
                && FIGURE_OBJECTS == 11
                && NOTEBOOK.size() == NOTEBOOK_ENTRIES
                && LETTER_FRAGMENTS == 9
                && !SHOW_COUNTERS;
    }

    /** 14.04 : exactement deux fiches contiennent ce qu'il n'a dit a personne. */
    public static boolean secretFilesCompliant() {
        int n = 0;
        for (PeopleFile f : PEOPLE.values()) {
            if (f.secret) {
                n++;
            }
        }
        return n == PEOPLE_FILES_WITH_SECRETS;
    }

    /** 10.14 : six emplacements vides a la fin du Chapitre 1. */
    public boolean endingPromiseValid() {
        return filledSlots() == 1 && LETTER_SLOTS - filledSlots() == LETTER_SLOTS_EMPTY_CH1;
    }
}
