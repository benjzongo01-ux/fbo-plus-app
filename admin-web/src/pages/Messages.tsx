import { doc, orderBy, updateDoc } from "firebase/firestore";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { dateCourte, Etat, filtrePays, useAction, useCollection, type Doc } from "../commun";
import { appeler, db } from "../firebase";
import { useSession } from "../session";

/** Conversations des distributeurs du pays avec le centre d'appel. */
export default function Messages() {
  const { role, pays } = useSession();
  const { docs, erreur } = useCollection("conversations", ...filtrePays(role, pays), orderBy("dernierAt", "desc"));
  const [ouverte, setOuverte] = useState<string | null>(null);

  return (
    <>
      <h1>Messages</h1>
      <div className="deux-colonnes">
        <div>
          <Etat docs={docs} erreur={erreur} vide="Aucun message pour le moment." />
          <ul className="conversations">
            {docs?.map((c) => (
              <li key={c.id} className={c.id === ouverte ? "selection" : ""} onClick={() => setOuverte(c.id)}>
                <div className="ligne-conv">
                  <strong>{c.nomFbo || "Distributeur"}</strong>
                  {(c.nonLusCentre ?? 0) > 0 && <span className="pastille">{c.nonLusCentre}</span>}
                </div>
                <div className="discret">{c.dernierMessage}</div>
                <div className="discret">{dateCourte(c.dernierAt)}{role === "super_admin" ? ` · ${c.pays}` : ""}</div>
              </li>
            ))}
          </ul>
        </div>
        {ouverte && <Fil key={ouverte} conversation={docs?.find((c) => c.id === ouverte)} fboId={ouverte} />}
      </div>
    </>
  );
}

function Fil({ fboId, conversation }: { fboId: string; conversation?: Doc }) {
  const { docs: messages } = useCollection(`conversations/${fboId}/messages`, orderBy("createdAt", "asc"));
  const [texte, setTexte] = useState("");
  const { enCours, erreur, lancer } = useAction();
  const bas = useRef<HTMLDivElement>(null);

  // Ouvrir la conversation vaut lecture : le compteur du centre revient à zéro.
  useEffect(() => {
    if ((conversation?.nonLusCentre ?? 0) > 0) updateDoc(doc(db, "conversations", fboId), { nonLusCentre: 0 }).catch(() => undefined);
    bas.current?.scrollIntoView({ block: "end" });
  }, [conversation?.nonLusCentre, messages?.length, fboId]);

  async function envoyer(e: FormEvent) {
    e.preventDefault();
    const ok = await lancer(() => appeler("envoyerMessage", { fboId, texte }));
    if (ok) setTexte("");
  }

  return (
    <section className="carte detail fil">
      <h2>{conversation?.nomFbo || "Distributeur"}</h2>
      <div className="messages">
        {messages?.map((m) => (
          <div key={m.id} className={`bulle ${m.duCentre ? "centre" : "fbo"}`}>
            {m.commandeId && <div className="discret">À propos de la commande {String(m.commandeId).slice(-6)}</div>}
            <div>{m.texte}</div>
            <div className="discret heure">{dateCourte(m.createdAt)}</div>
          </div>
        ))}
        <div ref={bas} />
      </div>
      <form className="actions" onSubmit={envoyer}>
        <input placeholder="Votre réponse" aria-label="Réponse" value={texte} maxLength={1000} onChange={(e) => setTexte(e.target.value)} required />
        <button className="principal" disabled={enCours || !texte.trim()}>Envoyer</button>
      </form>
      {erreur && <p className="erreur">{erreur}</p>}
    </section>
  );
}
