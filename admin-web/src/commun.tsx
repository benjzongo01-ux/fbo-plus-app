import { collection, onSnapshot, query, where, type Query, type QueryConstraint } from "firebase/firestore";
import { useEffect, useState } from "react";
import { db } from "./firebase";

export type Doc = { id: string } & Record<string, any>;

export const ROLES_PERSONNEL = ["operateur", "gestionnaire_stock", "admin", "super_admin"];

export const PAYS = ["Burkina Faso", "Côte d'Ivoire", "Sénégal", "Guinée"];

/** Filtre « mon pays » : le super administrateur voit tous les pays. */
export const filtrePays = (role: string, pays: string): QueryConstraint[] =>
  role === "super_admin" ? [] : [where("pays", "==", pays)];

export const LIBELLES_ROLES: Record<string, string> = {
  fbo: "Distributeur",
  livreur: "Livreur",
  operateur: "Opérateur",
  gestionnaire_stock: "Gestionnaire de stock",
  correspondant: "Correspondant au siège",
  admin: "Administrateur",
  super_admin: "Super administrateur",
};

export const LIBELLES_STATUTS: Record<string, string> = {
  nouvelle: "Nouvelle",
  confirmee: "Confirmée",
  en_preparation: "En préparation",
  prete: "Prête",
  assignee: "Livreur assigné",
  en_livraison: "En livraison",
  livree: "Livrée",
  validee: "Validée sur l'ID",
  expediee: "Expédiée",
  annulee: "Annulée",
  retournee: "Retournée",
};

export const fcfa = (n: number | undefined) =>
  `${new Intl.NumberFormat("fr-FR").format(n ?? 0)} FCFA`;

export function dateCourte(valeur: { toDate?: () => Date } | undefined) {
  const d = valeur?.toDate?.();
  return d
    ? d.toLocaleString("fr-FR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })
    : "—";
}

/** Traduit les erreurs Firebase en un message lisible, sans détail technique. */
const MESSAGES_CONNEXION: Record<string, string> = {
  "auth/invalid-credential": "Email ou mot de passe incorrect.",
  "auth/wrong-password": "Email ou mot de passe incorrect.",
  "auth/user-not-found": "Email ou mot de passe incorrect.",
  "auth/invalid-email": "Adresse email invalide.",
  "auth/email-already-in-use": "Cette adresse a déjà un compte : connectez-vous, ou utilisez « Mot de passe oublié ».",
  "auth/weak-password": "Mot de passe trop faible : 8 caractères au moins.",
  "auth/popup-closed-by-user": "Connexion Google annulée.",
  "auth/cancelled-popup-request": "Connexion Google annulée.",
  "auth/popup-blocked": "Le navigateur a bloqué la fenêtre Google : autorisez les fenêtres pour ce site.",
  "auth/too-many-requests": "Trop de tentatives. Réessayez dans quelques minutes.",
  "auth/network-request-failed": "Pas de connexion Internet.",
};

export function messageErreur(e: unknown): string {
  const { code, message } = e as { code?: string; message?: string };
  if (code && MESSAGES_CONNEXION[code]) return MESSAGES_CONNEXION[code];
  if (code?.startsWith("functions/") && message && code !== "functions/internal") return message;
  if (code === "permission-denied" || code === "functions/permission-denied") {
    return "Vous n'avez pas le droit d'effectuer cette action.";
  }
  return "Une erreur est survenue. Réessayez.";
}

/** Abonnement temps réel à une collection ; `erreur` évite une page vide silencieuse. */
export function useCollection(nom: string | null, ...contraintes: QueryConstraint[]) {
  const [docs, setDocs] = useState<Doc[] | null>(null);
  const [erreur, setErreur] = useState("");
  // La clé suit aussi les valeurs filtrées (le pays), pas seulement le type des contraintes.
  const cle = `${nom}|${JSON.stringify(contraintes)}`;

  useEffect(() => {
    if (!nom) return;
    const requete: Query = query(collection(db, nom), ...contraintes);
    return onSnapshot(
      requete,
      (r) => {
        setDocs(r.docs.map((d) => ({ id: d.id, ...d.data() })));
        setErreur("");
      },
      (e) => setErreur(messageErreur(e)),
    );
    // Les contraintes de chaque page sont fixes : le nom de la collection suffit comme clé.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cle]);

  return { docs, erreur };
}

export function Etat({ docs, erreur, vide }: { docs: Doc[] | null; erreur: string; vide: string }) {
  if (erreur) return <p className="erreur">{erreur}</p>;
  if (docs === null) return <p className="discret">Chargement…</p>;
  if (docs.length === 0) return <p className="discret">{vide}</p>;
  return null;
}

/** Exécute une action serveur et expose son état au bouton et au message d'erreur. */
export function useAction() {
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState("");

  async function lancer(action: () => Promise<unknown>) {
    setEnCours(true);
    setErreur("");
    try {
      await action();
      return true;
    } catch (e) {
      console.error(e);
      setErreur(messageErreur(e));
      return false;
    } finally {
      setEnCours(false);
    }
  }

  return { enCours, erreur, lancer };
}
