import { doc, onSnapshot, type Timestamp } from "firebase/firestore";
import { useEffect, useState, type FormEvent } from "react";
import { useAction } from "./commun";
import { appeler, db } from "./firebase";

export interface EtatMaintenance {
  actif: boolean;
  message: string;
  finPrevue: Date | null;
}

/** État de maintenance en temps réel : lisible par tous, même sans compte. */
export function useMaintenance(): EtatMaintenance | null {
  const [etat, setEtat] = useState<EtatMaintenance | null>(null);
  useEffect(
    () =>
      onSnapshot(
        doc(db, "parametres", "maintenance"),
        (d) => {
          const v = d.data();
          setEtat({
            actif: v?.actif === true,
            message: String(v?.message ?? ""),
            finPrevue: (v?.finPrevue as Timestamp | null | undefined)?.toDate() ?? null,
          });
        },
        // Lecture impossible (hors ligne) : on n'empêche pas de travailler.
        () => setEtat({ actif: false, message: "", finPrevue: null }),
      ),
    [],
  );
  return etat;
}

/** « vers 15 h 30 (dans environ 45 min) », ou « très bientôt » si l'heure est dépassée ou inconnue. */
export function retourPrevu(fin: Date | null, maintenant = new Date()): string {
  if (!fin) return "très bientôt";
  const minutes = Math.round((fin.getTime() - maintenant.getTime()) / 60_000);
  if (minutes <= 0) return "dans quelques instants";
  const heure = fin.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }).replace(":", " h ");
  const jour = fin.toDateString() === maintenant.toDateString() ? "" : ` le ${fin.toLocaleDateString("fr-FR")}`;
  const duree = minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, "0")}`;
  return `vers ${heure}${jour} (dans environ ${duree})`;
}

/** Page affichée à tout le personnel (sauf le super administrateur) pendant la maintenance. */
export function EcranMaintenance({ etat }: { etat: EtatMaintenance }) {
  const [, rafraichir] = useState(0);
  useEffect(() => {
    const minuterie = setInterval(() => rafraichir((n) => n + 1), 30_000);
    return () => clearInterval(minuterie);
  }, []);

  return (
    <div className="maintenance">
      <div className="logo">FBO+</div>
      <h1>Maintenance en cours</h1>
      <p>{etat.message}</p>
      <p className="retour">Retour prévu {retourPrevu(etat.finPrevue)}.</p>
      <p className="discret">
        Nous vous prions de nous excuser pour la gêne occasionnée. Vos données sont en sécurité ; cette page se
        rouvrira d'elle-même dès la fin de la maintenance.
      </p>
    </div>
  );
}

/** Commande de la maintenance, dans Paramètres : réservée au super administrateur. */
export function PanneauMaintenance() {
  const etat = useMaintenance();
  const action = useAction();
  const [message, setMessage] = useState("");
  const [duree, setDuree] = useState("60");

  function activer(e: FormEvent) {
    e.preventDefault();
    if (!window.confirm("Mettre FBO+ en maintenance ? Les distributeurs, livreurs et administrateurs de pays ne pourront plus rien faire jusqu'à la fin.")) return;
    action.lancer(() => appeler("definirMaintenance", { actif: true, message, dureeMinutes: Number(duree) || 0 }));
  }

  return (
    <section className="carte etroit">
      <h2>Mode maintenance</h2>
      {etat?.actif ? (
        <>
          <p className="alerte">Maintenance active : retour prévu {retourPrevu(etat.finPrevue)}.</p>
          <p className="discret">Message affiché : {etat.message}</p>
          <button
            className="principal"
            disabled={action.enCours}
            onClick={() => action.lancer(() => appeler("definirMaintenance", { actif: false }))}
          >
            Terminer la maintenance
          </button>
        </>
      ) : (
        <form onSubmit={activer}>
          <p className="discret">
            Les trois applications affichent un écran d'attente poli et le serveur refuse toute action, sauf les vôtres.
          </p>
          <label>
            Message aux utilisateurs (facultatif)
            <textarea
              value={message}
              maxLength={500}
              rows={3}
              placeholder="Nous améliorons FBO+ pour mieux vous servir. Merci de votre patience…"
              onChange={(e) => setMessage(e.target.value)}
            />
          </label>
          <label>
            Durée prévue (minutes, 0 si inconnue)
            <input type="number" min={0} max={10080} value={duree} onChange={(e) => setDuree(e.target.value)} />
          </label>
          <button className="danger" disabled={action.enCours}>Activer la maintenance</button>
        </form>
      )}
      {action.erreur && <p className="erreur">{action.erreur}</p>}
    </section>
  );
}
