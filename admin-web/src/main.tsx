import { signOut } from "firebase/auth";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, NavLink, Navigate, Route, Routes } from "react-router-dom";
import { LIBELLES_ROLES } from "./commun";
import { auth, surEmulateurs } from "./firebase";
import Boutique from "./pages/Boutique";
import Commandes from "./pages/Commandes";
import Connexion from "./pages/Connexion";
import Livreurs from "./pages/Livreurs";
import Parametres from "./pages/Parametres";
import Produits from "./pages/Produits";
import Stocks from "./pages/Stocks";
import Utilisateurs from "./pages/Utilisateurs";
import { FournisseurSession, useSession } from "./session";
import "./style.css";

const ADMIN = ["admin", "super_admin"];
const PAGES = [
  { chemin: "/commandes", titre: "Centre d'appel", roles: ["operateur", "gestionnaire_stock", ...ADMIN], page: <Commandes /> },
  { chemin: "/stocks", titre: "Stocks", roles: ["gestionnaire_stock", ...ADMIN], page: <Stocks /> },
  { chemin: "/produits", titre: "Produits", roles: ADMIN, page: <Produits /> },
  { chemin: "/livreurs", titre: "Livreurs", roles: ["operateur", ...ADMIN], page: <Livreurs /> },
  { chemin: "/utilisateurs", titre: "Utilisateurs", roles: ADMIN, page: <Utilisateurs /> },
  { chemin: "/parametres", titre: "Paramètres", roles: ADMIN, page: <Parametres /> },
];

function Application() {
  const { chargement, utilisateur, role } = useSession();
  if (chargement) return <p className="discret centre">Chargement…</p>;
  if (!utilisateur) return <Connexion />;

  const pages = PAGES.filter((p) => p.roles.includes(role));
  return (
    <div className="cadre">
      <aside className="menu">
        <div className="logo">FBO+ <span>Admin</span></div>
        <nav>
          {pages.map((p) => (
            <NavLink key={p.chemin} to={p.chemin}>{p.titre}</NavLink>
          ))}
        </nav>
        <div className="pied-menu">
          <div>{utilisateur.phoneNumber}</div>
          <div className="discret">{LIBELLES_ROLES[role]}</div>
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
              <Application />
            </FournisseurSession>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
