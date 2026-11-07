import { collection, doc, getDoc, getDocs, query, where } from "firebase/firestore";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { fcfa, type Doc } from "../commun";
import { db } from "../firebase";

/** Page publique du QR code : catalogue du distributeur, sans compte ni données privées. */
export default function Boutique() {
  const { uid } = useParams();
  const [boutique, setBoutique] = useState<Doc | null | undefined>(undefined);
  const [produits, setProduits] = useState<Doc[]>([]);
  const [categorie, setCategorie] = useState("Tous");

  useEffect(() => {
    getDoc(doc(db, "boutiques", uid!))
      .then((d) => setBoutique(d.exists() ? { id: d.id, ...d.data() } : null))
      .catch(() => setBoutique(null));
    getDocs(query(collection(db, "products"), where("actif", "==", true)))
      .then((r) => setProduits(r.docs.map((d) => ({ id: d.id, ...d.data() }))));
  }, [uid]);

  if (boutique === undefined) return <p className="discret centre">Chargement…</p>;
  if (boutique === null) return <p className="discret centre">Cette boutique n'existe pas.</p>;

  const categories = ["Tous", ...new Set(produits.map((p) => String(p.categorie)))];
  const whatsapp = String(boutique.whatsapp ?? "").replace(/\D/g, "");
  const visibles = produits.filter((p) => categorie === "Tous" || p.categorie === categorie);
  const lien = (texte: string) => `https://wa.me/${whatsapp}?text=${encodeURIComponent(texte)}`;

  return (
    <div className="boutique">
      <header>
        <div className="logo">FBO+</div>
        <h1>{boutique.nom || "Boutique Forever"}</h1>
        {boutique.ville && <p className="discret">{boutique.ville}{boutique.pays ? `, ${boutique.pays}` : ""}</p>}
        {whatsapp && (
          <a className="bouton principal" href={lien("Bonjour, je souhaite des informations sur vos produits Forever.")}>
            Écrire sur WhatsApp
          </a>
        )}
      </header>
      <div className="onglets">
        {categories.map((c) => (
          <button key={c} className={c === categorie ? "actif" : ""} onClick={() => setCategorie(c)}>{c}</button>
        ))}
      </div>
      <div className="grille">
        {visibles.map((p) => (
          <article key={p.id} className="carte">
            {/* Photo fournie par le produit, sinon celle de l'application (scripts/copier-photos-boutique.mjs). */}
            <img
              src={p.image || `/produits/${encodeURIComponent(String(p.reference ?? p.id))}.jpg`}
              alt={p.nom}
              loading="lazy"
              onError={(e) => { e.currentTarget.style.display = "none"; }}
            />
            <h2>{p.nom}</h2>
            <p className="discret">{p.description}</p>
            <p><strong>{fcfa(p.prix)}</strong></p>
            {whatsapp && <a href={lien(`Bonjour, je souhaite commander : ${p.nom}.`)}>Commander</a>}
          </article>
        ))}
      </div>
    </div>
  );
}
