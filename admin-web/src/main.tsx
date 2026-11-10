import { signOut } from "firebase/auth";
import { where } from "firebase/firestore";
import { StrictMode, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, NavLink, Navigate, Route, Routes } from "react-router-dom";
import { filtrePays, LIBELLES_ROLES, useCollection } from "./commun";
import { auth, surEmulateurs } from "./firebase";
import Boutique from "./pages/Boutique";
import Commandes from "./pages/Commandes";
import Connexion from "./pages/Connexion";
import Livreurs from "./pages/Livreurs";
import Reappro from "./pages/Reappro";
import Identites from "./pages/Identites";
import Messages from "./pages/Messages";
import ValidationsSiege from "./pages/ValidationsSiege";
import Parametres from "./pages/Parametres";
import Produits from "./pages/Produits";
import Stocks from "./pages/Stocks";
import Utilisateurs from "./pages/Utilisateurs";
import { EcranMaintenance, useMaintenance } from "./maintenance";
import { FournisseurSession, useSession } from "./session";
import "./style.css";

const ADMIN = ["admin", "super_admin"];
// Réglages, catalogue et comptes : le super administrateur seul. Les admins de pays traitent les commandes.
const SUPER = ["super_admin"];
const PAGES = [
  { chemin: "/commandes", titre: "Centre d'appel", roles: ["operateur", "gestionnaire_stock", ...ADMIN], page: <Commandes /> },
  { chemin: "/reappro", titre: "Réapprovisionnement", roles: ["gestionnaire_stock", ...ADMIN], page: <Reappro /> },
  { chemin: "/stocks", titre: "Stocks", roles: ["gestionnaire_stock", ...ADMIN], page: <Stocks /> },
  { chemin: "/produits", titre: "Produits", roles: SUPER, page: <Produits /> },
  { chemin: "/messages", titre: "Messages", roles: ["operateur", ...ADMIN], page: <Messages /> },
  { chemin: "/livreurs", titre: "Livreurs", roles: ["operateur", ...ADMIN], page: <Livreurs /> },
  { chemin: "/validations", titre: "Validations siège", roles: ["operateur", ...ADMIN], page: <ValidationsSiege /> },
  { chemin: "/identites", titre: "Identités livreurs", roles: SUPER, page: <Identites /> },
  { chemin: "/utilisateurs", titre: "Utilisateurs", roles: SUPER, page: <Utilisateurs /> },
  { chemin: "/parametres", titre: "Paramètres", roles: SUPER, page: <Parametres /> },
];

function Application() {
  const { chargement, utilisateur } = useSession();
  if (chargement) return <p className="discret centre">Chargement…</p>;
  if (!utilisateur) return <Connexion />;

  return <Cadre />;
}

/** Pendant la maintenance, seul le super administrateur garde l'accès (pour la faire). */
function AvecMaintenance({ children }: { children: ReactNode }) {
  const maintenance = useMaintenance();
  const { role } = useSession();
  if (maintenance?.actif && role !== "super_admin") return <EcranMaintenance etat={maintenance} />;
  return (
    <>
      {maintenance?.actif && (
        <div className="bandeau alerte-maintenance">
          Maintenance en cours : les utilisateurs voient l'écran d'attente. Terminez-la dans Paramètres.
        </div>
      )}
      {children}
    </>
  );
}

function Cadre() {
  const { utilisateur, role, pays } = useSession();
  const { docs: nouvelles } = useCollection("orders", where("statut", "==", "nouvelle"), ...filtrePays(role, pays));
  const aTraiter = nouvelles?.length ?? 0;
  const { docs: conversations } = useCollection(
    ["operateur", ...ADMIN].includes(role) ? "conversations" : null,
    where("nonLusCentre", ">", 0),
    ...filtrePays(role, pays),
  );
  const nonLus = conversations?.length ?? 0;
  const pages = PAGES.filter((p) => p.roles.includes(role));
  return (
    <div className="cadre">
      <aside className="menu">
        <div className="logo">FBO+ <span>Admin</span></div>
        <nav>
          {pages.map((p) => (
            <NavLink key={p.chemin} to={p.chemin}>
              {p.titre}
              {p.chemin === "/commandes" && aTraiter > 0 && <span className="pastille">{aTraiter}</span>}
              {p.chemin === "/messages" && nonLus > 0 && <span className="pastille">{nonLus}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="pied-menu">
          <div>{utilisateur?.phoneNumber}</div>
          <div className="discret">{LIBELLES_ROLES[role]}{role === "super_admin" ? " · Tous les pays" : ` · ${pays}`}</div>
          <button className="lien" onClick={() => signOut(auth)}>Se déconnecter</button>
        </div>
      </aside>
      <main className="contenu">
        {surEmulateurs && <div className="bandeau">Environnement de test : émulateurs locaux</div>}
        <Routes>
          {pages.map((p) => (
            <Route key={p.chemin} path={p.chemin} element={p.page} />
          ))}
          <Route path="*" element={<Navigate to={pages[0].chemin} replace />} />
        </Routes>
      </main>
    </div>
  );
}

createRoot(document.getElementById("racine")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/boutique/:uid" element={<Boutique />} />
        <Route
          path="*"
          element={
            <FournisseurSession>
              <AvecMaintenance>
                <Application />
              </AvecMaintenance>
            </FournisseurSession>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
