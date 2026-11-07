import { onIdTokenChanged, signOut, type User } from "firebase/auth";
import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { ROLES_PERSONNEL } from "./commun";
import { auth } from "./firebase";

interface Session {
  chargement: boolean;
  utilisateur: User | null;
  role: string;
  pays: string;
  refuse: boolean;
}

const Contexte = createContext<Session>({ chargement: true, utilisateur: null, role: "", pays: "", refuse: false });

export function FournisseurSession({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({
    chargement: true,
    utilisateur: null,
    role: "",
    pays: "",
    refuse: false,
  });

  useEffect(
    () =>
      onIdTokenChanged(auth, async (utilisateur) => {
        if (!utilisateur) {
          setSession((s) => ({ chargement: false, utilisateur: null, role: "", pays: "", refuse: s.refuse }));
          return;
        }
        // Une session révoquée (suspension, changement de rôle) échoue ici : retour à la connexion.
        const jeton = await utilisateur.getIdTokenResult().catch(() => null);
        const role = jeton ? String(jeton.claims.role ?? "") : null;
        const pays = String(jeton?.claims.pays ?? "");
        if (role === null) {
          setSession({ chargement: false, utilisateur: null, role: "", pays: "", refuse: false });
          await signOut(auth).catch(() => undefined);
          return;
        }
        if (!ROLES_PERSONNEL.includes(role)) {
          setSession({ chargement: false, utilisateur: null, role: "", pays: "", refuse: true });
          await signOut(auth);
          return;
        }
        setSession({ chargement: false, utilisateur, role, pays, refuse: false });
      }),
    [],
  );

  return <Contexte.Provider value={session}>{children}</Contexte.Provider>;
}

export const useSession = () => useContext(Contexte);
