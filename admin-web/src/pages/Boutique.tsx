import { signInAnonymously } from "firebase/auth";
import { collection, doc, getDoc, getDocs, query, where } from "firebase/firestore";
import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { fcfa, messageErreur, type Doc } from "../commun";
import { appeler, auth, db } from "../firebase";
import { EcranMaintenance, useMaintenance } from "../maintenance";

/**
 * Boutique publique d'un distributeur (QR code ou lien) : le client choisit ses produits et commande
 * en ligne. La commande arrive dans FBO+, chez le distributeur, qui la vérifie et la fait livrer.
 */
export default function Boutique() {
  const { uid } = useParams();
  const [boutique, setBoutique] = useState<Doc | null | undefined>(undefined);
  const [produits, setProduits] = useState<Doc[]>([]);
  const [categorie, setCategorie] = useState("Tous");
  const [panier, setPanier] = useState<Record<string, number>>({});
  const [commander, setCommander] = useState(false);
  const [envoyee, setEnvoyee] = useState<{ total: number } | null>(null);
  const maintenance = useMaintenance();

  useEffect(() => {
    getDoc(doc(db, "boutiques", uid!))
      .then((d) => setBoutique(d.exists() ? { id: d.id, ...d.data() } : null))
      .catch(() => setBoutique(null));
    getDocs(query(collection(db, "products"), where("actif", "==", true)))
      .then((r) => setProduits(r.docs.map((d) => ({ id: d.id, ...d.data() }))));
  }, [uid]);

  if (maintenance?.actif) return <EcranMaintenance etat={maintenance} />;
  if (boutique === undefined) return <p className="discret centre">Chargement…</p>;
  if (boutique === null) return <p className="discret centre">Cette boutique n'existe pas.</p>;

  const categories = ["Tous", ...new Set(produits.map((p) => String(p.categorie)))];
  const whatsapp = String(boutique.whatsapp ?? "").replace(/\D/g, "");
  const visibles = produits.filter((p) => categorie === "Tous" || p.categorie === categorie);
  const lien = (texte: string) => `https://wa.me/${whatsapp}?text=${encodeURIComponent(texte)}`;
  const choisis = produits.filter((p) => (panier[p.id] ?? 0) > 0);
  const total = choisis.reduce((s, p) => s + p.prix * panier[p.id], 0);
  const articles = choisis.reduce((s, p) => s + panier[p.id], 0);
  const changer = (id: string, delta: number) =>
    setPanier((c) => ({ ...c, [id]: Math.max(0, Math.min(100, (c[id] ?? 0) + delta)) }));

  if (envoyee) {
    return (
      <div className="boutique">
        <div className="carte merci">
          <div className="logo">FBO+</div>
          <h1>Merci pour votre commande !</h1>
          <p>
            Votre commande de <strong>{fcfa(envoyee.total)}</strong> est bien arrivée chez {boutique.nom || "votre distributeur"}.
            Il vous appellera très vite pour confirmer la livraison.
          </p>
          {whatsapp && <a className="bouton principal" href={lien("Bonjour, je viens de passer une commande dans votre boutique.")}>Écrire sur WhatsApp</a>}
          <button className="lien" onClick={() => { setEnvoyee(null); setPanier({}); }}>Revenir à la boutique</button>
        </div>
      </div>
    );
  }

  return (
    <div className="boutique">
      <header>
        <div className="logo">FBO+</div>
        <h1>{boutique.nom || "Boutique Forever"}</h1>
        <p className="discret">
          Distributeur Forever{boutique.idForever ? ` · ID ${boutique.idForever}` : ""}
          {boutique.ville ? ` · ${boutique.ville}${boutique.pays ? `, ${boutique.pays}` : ""}` : ""}
        </p>
        {whatsapp && (
          <a className="bouton secondaire" href={lien("Bonjour, je souhaite des informations sur vos produits Forever.")}>
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
            {(panier[p.id] ?? 0) > 0 ? (
              <div className="quantites">
                <button aria-label={`Retirer un ${p.nom}`} onClick={() => changer(p.id, -1)}>−</button>
                <span>{panier[p.id]}</span>
                <button aria-label={`Ajouter un ${p.nom}`} onClick={() => changer(p.id, 1)}>+</button>
              </div>
            ) : (
              <button className="principal" onClick={() => changer(p.id, 1)}>Ajouter</button>
            )}
          </article>
        ))}
      </div>

      {articles > 0 && !commander && (
        <div className="barre-panier">
          <span>{articles} article{articles > 1 ? "s" : ""} · <strong>{fcfa(total)}</strong></span>
          <button className="principal" onClick={() => setCommander(true)}>Commander</button>
        </div>
      )}
      {commander && (
        <FormulaireCommande
          boutiqueId={uid!}
          choisis={choisis}
          panier={panier}
          total={total}
          onAnnuler={() => setCommander(false)}
          onEnvoyee={(t) => { setCommander(false); setEnvoyee({ total: t }); }}
        />
      )}
    </div>
  );
}

function FormulaireCommande({
  boutiqueId,
  choisis,
  panier,
  total,
  onAnnuler,
  onEnvoyee,
}: {
  boutiqueId: string;
  choisis: Doc[];
  panier: Record<string, number>;
  total: number;
  onAnnuler: () => void;
  onEnvoyee: (total: number) => void;
}) {
  const [client, setClient] = useState({ nom: "", telephone: "", telephone2: "", quartier: "", adresse: "", note: "" });
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState("");
  const champ = (cle: keyof typeof client) => ({
    value: client[cle],
    onChange: (e: { target: { value: string } }) => setClient({ ...client, [cle]: e.target.value }),
  });

  async function envoyer(e: FormEvent) {
    e.preventDefault();
    setEnCours(true);
    setErreur("");
    try {
      // Session anonyme gratuite : elle permet de limiter les envois par appareil.
      if (!auth.currentUser) await signInAnonymously(auth);
      const r = await appeler<{ total: number }>("commanderEnBoutique", {
        boutiqueId,
        clientNom: client.nom,
        clientTelephone: client.telephone,
        clientTelephone2: client.telephone2,
        quartier: client.quartier,
        adresse: client.adresse,
        note: client.note,
        lignes: choisis.map((p) => ({ produitId: p.id, quantite: panier[p.id] })),
      });
      onEnvoyee(r.total);
    } catch (e) {
      setErreur(messageErreur(e));
    } finally {
      setEnCours(false);
    }
  }

  return (
    <div className="fond-modal">
      <form className="carte modal" onSubmit={envoyer}>
        <h2>Votre commande</h2>
        <ul className="liste">
          {choisis.map((p) => (
            <li key={p.id}><span>{p.nom} × {panier[p.id]}</span><span>{fcfa(p.prix * panier[p.id])}</span></li>
          ))}
        </ul>
        <p className="droite"><strong>Total : {fcfa(total)}</strong> <span className="discret">(hors livraison)</span></p>
        <label>Nom et prénom<input {...champ("nom")} autoComplete="name" required /></label>
        <label>Téléphone<input {...champ("telephone")} type="tel" placeholder="+226 70 00 00 00" required /></label>
        <label>Deuxième téléphone (facultatif)<input {...champ("telephone2")} type="tel" /></label>
        <label>Quartier<input {...champ("quartier")} required /></label>
        <label>Adresse, repère (facultatif)<input {...champ("adresse")} /></label>
        <label>Message pour le distributeur (facultatif)<textarea {...champ("note")} rows={2} maxLength={500} /></label>
        {erreur && <p className="erreur">{erreur}</p>}
        <div className="actions">
          <button className="principal" disabled={enCours}>{enCours ? "Envoi…" : "Envoyer ma commande"}</button>
          <button type="button" className="lien" onClick={onAnnuler}>Modifier mon panier</button>
        </div>
        <p className="discret">Le distributeur vous appelle pour confirmer la commande et les frais de livraison.</p>
      </form>
    </div>
  );
}
