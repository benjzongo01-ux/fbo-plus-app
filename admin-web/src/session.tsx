import { onIdTokenChanged, signOut, type User } from "firebase/auth";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { messageErreur, ROLES_PERSONNEL } from "./commun";
import { appeler, auth } from "./firebase";

interface Session {
  chargement: boolean;
  utilisateur: User | null;
  role: string;
  pays: string;
  /** Message affiché sur la page de connexion quand l'accès est refusé. */
  refus: string;
}

const VIDE: Session = { chargement: false, utilisateur: null, role: "", pays: "", refus: "" };
const Contexte = createContext<Session>({ ...VIDE, chargement: true });

export function FournisseurSession({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({ ...VIDE, chargement: true });

  useEffect(
    () =>
      onIdTokenChanged(auth, async (utilisateur) => {
        if (!utilisateur) {
          setSession((s) => ({ ...VIDE, refus: s.refus }));
          return;
        }
        // Une session révoquée (suspension, changement de rôle) échoue ici : retour à la connexion.
        const jeton = await utilisateur.getIdTokenResult().catch(() => null);
        if (!jeton) {
          setSession(VIDE);
          await signOut(auth).catch(() => undefined);
          return;
        }

        const role = String(jeton.claims.role ?? "");
        if (!role) {
          // Première connexion : le serveur applique l'invitation du super administrateur, ou refuse.
          try {
            await appeler("initialiserProfil", { personnel: true });
            await utilisateur.getIdToken(true); // relance ce même écouteur, avec le rôle
          } catch (e) {
            const code = (e as { code?: string }).code;
            // Adresse jamais invitée : le compte vide créé par cette connexion est retiré.
            if (code === "functions/permission-denied") await utilisateur.delete().catch(() => undefined);
            setSession({ ...VIDE, refus: messageErreur(e) });
            await signOut(auth).catch(() => undefined);
          }
          return;
        }

        if (!ROLES_PERSONNEL.includes(role)) {
          setSession({ ...VIDE, refus: "Ce compte n'a pas accès à FBO+ Admin." });
          await signOut(auth);
          return;
        }
        setSession({ chargement: false, utilisateur, role, pays: String(jeton.claims.pays ?? ""), refus: "" });
      }),
    [],
  );

  return <Contexte.Provider value={session}>{children}</Contexte.Provider>;
}

export const useSession = () => useContext(Contexte);
