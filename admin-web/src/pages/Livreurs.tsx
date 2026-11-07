import { where } from "firebase/firestore";
import { Etat, filtrePays, useCollection } from "../commun";
import { useSession } from "../session";

export default function Livreurs() {
  const { role, pays } = useSession();
  const { docs: livreurs, erreur } = useCollection("users", where("role", "==", "livreur"), ...filtrePays(role, pays));
  const { docs: livraisons } = useCollection("deliveries", ...filtrePays(role, pays));

  return (
    <>
      <h1>Livreurs</h1>
      <p className="discret">L'attribution se fait depuis le centre d'appel. La création des comptes est dans Utilisateurs.</p>
      <Etat docs={livreurs} erreur={erreur} vide="Aucun livreur pour le moment." />
      {livreurs && livreurs.length > 0 && (
        <table>
          <thead>
            <tr><th>Nom</th><th>Téléphone</th><th className="droite">En cours</th><th className="droite">Livrées</th><th className="droite">Retours</th><th className="droite">Réussite</th><th>Dernière position</th></tr>
          </thead>
          <tbody>
            {livreurs.map((l) => {
              const siennes = livraisons?.filter((d) => d.livreurId === l.id) ?? [];
              const livrees = siennes.filter((d) => d.statut === "livree").length;
              const retours = siennes.filter((d) => d.statut === "retournee").length;
              const enCours = siennes.filter((d) => ["assignee", "en_livraison"].includes(d.statut));
              const terminees = livrees + retours;
              const position = enCours.find((d) => d.position)?.position;
              return (
                <tr key={l.id}>
                  <td>{l.nom}</td>
                  <td>{l.telephone}</td>
                  <td className="droite">{enCours.length}</td>
                  <td className="droite">{livrees}</td>
                  <td className="droite">{retours}</td>
                  <td className="droite">{terminees ? `${Math.round((livrees / terminees) * 100)} %` : "—"}</td>
                  <td>
                    {position ? (
                      <a
                        href={`https://www.openstreetmap.org/?mlat=${position.lat}&mlon=${position.lng}#map=16/${position.lat}/${position.lng}`}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Voir sur la carte
                      </a>
                    ) : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </>
  );
}
