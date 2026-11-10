import { where } from "firebase/firestore";
import { getDownloadURL, ref } from "firebase/storage";
import { useEffect, useState } from "react";
import { dateCourte, Etat, useAction, useCollection, type Doc } from "../commun";
import { appeler, stockage } from "../firebase";

/** Dossiers d'identité des livreurs à vérifier : réservé au super administrateur. */
export default function Identites() {
  const { docs: dossiers, erreur } = useCollection("kyc");
  const { docs: livreurs } = useCollection("users", where("role", "==", "livreur"));
  const [voirTous, setVoirTous] = useState(false);
  const statut = (uid: string) => livreurs?.find((l) => l.id === uid)?.kyc?.statut ?? "aucun";
  const visibles = dossiers?.filter((d) => voirTous || statut(d.id) === "en_attente") ?? null;

  return (
    <>
      <h1>Identités des livreurs</h1>
      <p className="discret">Aucune mission n'est confiée à un livreur tant que son dossier n'est pas validé ici.</p>
      <label className="case marge">
        <input type="checkbox" checked={voirTous} onChange={(e) => setVoirTous(e.target.checked)} />
        Voir aussi les dossiers déjà traités
      </label>
      <Etat docs={visibles} erreur={erreur} vide="Aucun dossier en attente." />
      <div className="lots">
        {visibles?.map((d) => <Dossier key={d.id} dossier={d} statut={statut(d.id)} />)}
      </div>
    </>
  );
}

function Photo({ chemin, titre }: { chemin: string; titre: string }) {
  const [url, setUrl] = useState("");
  useEffect(() => {
    getDownloadURL(ref(stockage, chemin)).then(setUrl).catch(() => setUrl(""));
  }, [chemin]);
  return (
    <figure className="piece">
      {url ? <a href={url} target="_blank" rel="noreferrer"><img src={url} alt={titre} /></a> : <div className="discret">Image indisponible</div>}
      <figcaption className="discret">{titre}</figcaption>
    </figure>
  );
}

function Dossier({ dossier, statut }: { dossier: Doc; statut: string }) {
  const { enCours, erreur, lancer } = useAction();
  return (
    <section className="carte">
      <h2>{dossier.nom || "Livreur"} <span className="discret">· {dossier.pays} · {statut}</span></h2>
      <p>
        Pièce n° <strong>{dossier.numeroPiece}</strong> · {dossier.adresse}<br />
        À contacter : {dossier.contactNom} · <a href={`tel:${dossier.contactTelephone}`}>{dossier.contactTelephone}</a><br />
        <span className="discret">Envoyé le {dateCourte(dossier.soumisLe)}</span>
      </p>
      <div className="pieces">
        <Photo chemin={dossier.pieceRecto} titre="Recto" />
        <Photo chemin={dossier.pieceVerso} titre="Verso" />
        <Photo chemin={dossier.selfie} titre="Visage" />
      </div>
      {statut === "en_attente" && (
        <div className="actions">
          <button className="principal" disabled={enCours} onClick={() => lancer(() => appeler("deciderKyc", { uid: dossier.id, valider: true }))}>
            Valider l'identité
          </button>
          <button
            className="danger"
            disabled={enCours}
            onClick={() => {
              const motif = window.prompt("Motif du refus (le livreur le verra) :", "Photo de la pièce illisible");
              if (motif) lancer(() => appeler("deciderKyc", { uid: dossier.id, valider: false, motif }));
            }}
          >
            Refuser
          </button>
        </div>
      )}
      {erreur && <p className="erreur">{erreur}</p>}
    </section>
  );
}
