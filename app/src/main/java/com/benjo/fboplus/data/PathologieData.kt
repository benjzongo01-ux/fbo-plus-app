package com.benjo.fboplus.data

data class PathologieInfo(
    val nom: String,
    val emoji: String,
    val description: String,
    val symptomes: String,
    val risquesSiNeglige: String,
    val produitsRefs: List<String>,
    val pathologiesSimilaires: List<String> = emptyList()
)

val PATHOLOGIES = listOf(
    PathologieInfo(
        nom = "Troubles digestifs",
        emoji = "🫃",
        description = "Ensemble de problèmes affectant le système digestif.",
        symptomes = "Ballonnements, douleurs abdominales, brûlures d'estomac, transit irrégulier, nausées, flatulences.",
        risquesSiNeglige = "Ulcères chroniques, malabsorption des nutriments, inflammation intestinale, risque accru de cancer colorectal.",
        produitsRefs = listOf("15", "34", "610", "464", "68", "504"),
        pathologiesSimilaires = listOf("Colopathie", "Gastrite", "Ulcère gastrique", "Reflux gastro-œsophagien", "Constipation")
    ),
    PathologieInfo(
        nom = "Hémorroïdes",
        emoji = "🩸",
        description = "Dilatation des veines de la région anale causant douleur, saignement et inconfort.",
        symptomes = "Saignements lors des selles, démangeaisons, douleurs anales, sensation de pesanteur.",
        risquesSiNeglige = "Anémie par saignements répétés, thrombose hémorroïdaire, complications chirurgicales.",
        produitsRefs = listOf("15", "464", "610", "37", "504"),
        pathologiesSimilaires = listOf("Hémorroïdes internes", "Hémorroïdes externes", "Fissures anales")
    ),
    PathologieInfo(
        nom = "Diabète",
        emoji = "🩺",
        description = "Maladie métabolique caractérisée par un excès de sucre dans le sang.",
        symptomes = "Soif excessive, urines fréquentes, fatigue, perte de poids inexpliquée, cicatrisation lente.",
        risquesSiNeglige = "Complications rénales, cardiaques, oculaires, neuropathie, amputation.",
        produitsRefs = listOf("15", "439", "376", "71", "464", "196"),
        pathologiesSimilaires = listOf("Diabète type 1", "Diabète type 2", "Prédiabète", "Résistance à l'insuline", "Hyperglycémie")
    ),
    PathologieInfo(
        nom = "Hypertension artérielle",
        emoji = "❤️",
        description = "Pression artérielle chroniquement élevée, facteur de risque majeur cardiovasculaire.",
        symptomes = "Maux de tête, vertiges, vision floue, essoufflement, bourdonnements d'oreilles.",
        risquesSiNeglige = "AVC, infarctus, insuffisance rénale, insuffisance cardiaque.",
        produitsRefs = listOf("376", "439", "15", "312", "65"),
        pathologiesSimilaires = listOf("Hypertension essentielle", "Hypertension secondaire", "Pression artérielle élevée", "Maladies cardiovasculaires")
    ),
    PathologieInfo(
        nom = "Troubles de la santé sexuelle masculine",
        emoji = "💪",
        description = "Impuissance, dysfonction érectile, faiblesse sexuelle, baisse de libido chez l'homme.",
        symptomes = "Difficultés à obtenir ou maintenir une érection, baisse du désir sexuel, fatigue générale.",
        risquesSiNeglige = "Problèmes relationnels, dépression, infertilité, signe d'une maladie cardiovasculaire sous-jacente.",
        produitsRefs = listOf("215", "27", "36", "439", "26", "188"),
        pathologiesSimilaires = listOf("Impuissance", "Dysfonction érectile", "Faiblesse sexuelle", "Baisse de libido", "Éjaculation précoce")
    ),
    PathologieInfo(
        nom = "Troubles de la prostate",
        emoji = "🔵",
        description = "Prostatite (inflammation) et hypertrophie bénigne de la prostate.",
        symptomes = "Difficultés à uriner, jet urinaire faible, envies fréquentes la nuit, douleurs pelviennes.",
        risquesSiNeglige = "Rétention urinaire aiguë, insuffisance rénale, risque de cancer de la prostate.",
        produitsRefs = listOf("215", "439", "34", "374", "26"),
        pathologiesSimilaires = listOf("Prostatite", "Hypertrophie bénigne de la prostate", "Adénome de la prostate", "Cancer de la prostate")
    ),
    PathologieInfo(
        nom = "Troubles de la fertilité",
        emoji = "👶",
        description = "Infertilité masculine et féminine, difficulté à concevoir.",
        symptomes = "Absence de grossesse après 12 mois de rapports non protégés, cycles irréguliers, qualité du sperme altérée.",
        risquesSiNeglige = "Infertilité définitive si non traitée, impact psychologique important.",
        produitsRefs = listOf("215", "36", "27", "439", "26"),
        pathologiesSimilaires = listOf("Infertilité masculine", "Infertilité féminine", "Stérilité", "Oligospermie", "Aménorrhée")
    ),
    PathologieInfo(
        nom = "Troubles gynécologiques",
        emoji = "🌸",
        description = "Fibromes, kystes ovariens, SOPK, douleurs menstruelles.",
        symptomes = "Règles douloureuses, saignements abondants, douleurs pelviennes, ballonnements, cycles irréguliers.",
        risquesSiNeglige = "Infertilité, anémie, complications chirurgicales, impact sur la qualité de vie.",
        produitsRefs = listOf("34", "27", "439", "612", "22", "375"),
        pathologiesSimilaires = listOf("Fibromes utérins", "Kystes ovariens", "SOPK", "Dysménorrhée", "Endométriose", "Ménopause")
    ),
    PathologieInfo(
        nom = "Infections urinaires et mycoses",
        emoji = "🦠",
        description = "Infections bactériennes des voies urinaires et infections fongiques.",
        symptomes = "Brûlures urinaires, démangeaisons, pertes vaginales, urines troubles ou malodorantes.",
        risquesSiNeglige = "Infection rénale, septicémie, infections récidivantes chroniques.",
        produitsRefs = listOf("15", "27"),
        pathologiesSimilaires = listOf("Cystite", "Urétrite", "Candidose", "Vaginite", "Infection urinaire récidivante")
    ),
    PathologieInfo(
        nom = "Douleurs articulaires et rhumatismales",
        emoji = "🦴",
        description = "Arthrose, rhumatismes, goutte — atteintes des articulations.",
        symptomes = "Douleurs articulaires, raideur matinale, gonflement, limitation des mouvements.",
        risquesSiNeglige = "Handicap fonctionnel, déformation articulaire, invalidité.",
        produitsRefs = listOf("196", "376", "439"),
        pathologiesSimilaires = listOf("Arthrose", "Arthrite", "Rhumatismes", "Goutte", "Polyarthrite rhumatoïde", "Ostéoporose")
    ),
    PathologieInfo(
        nom = "Douleurs musculaires et du dos",
        emoji = "🔙",
        description = "Lombalgie, sciatique, contractures musculaires.",
        symptomes = "Douleurs dans le bas du dos, irradiation dans les jambes, raideur musculaire, crampes.",
        risquesSiNeglige = "Hernie discale, compression nerveuse, invalidité chronique.",
        produitsRefs = listOf("196", "64", "376", "504"),
        pathologiesSimilaires = listOf("Lombalgie", "Sciatique", "Contractures musculaires", "Crampes", "Hernie discale", "Cervicalgie")
    ),
    PathologieInfo(
        nom = "Surpoids et gestion du poids",
        emoji = "⚖️",
        description = "Surpoids et obésité, accumulation excessive de masse graisseuse.",
        symptomes = "IMC élevé, essoufflement à l'effort, fatigue, douleurs articulaires, mauvaise estime de soi.",
        risquesSiNeglige = "Diabète type 2, maladies cardiovasculaires, apnée du sommeil, cancers.",
        produitsRefs = listOf("71", "289", "659", "547"),
        pathologiesSimilaires = listOf("Surpoids", "Obésité", "Ventre gonflé", "Rétention d'eau", "Cellulite")
    ),
    PathologieInfo(
        nom = "Fatigue et manque d'énergie",
        emoji = "⚡",
        description = "Fatigue chronique et faiblesse générale affectant la qualité de vie.",
        symptomes = "Épuisement permanent, manque de motivation, difficultés de concentration, faiblesse musculaire.",
        risquesSiNeglige = "Dépression, burn-out, affaiblissement du système immunitaire, perte de productivité.",
        produitsRefs = listOf("622", "36", "27", "721", "504"),
        pathologiesSimilaires = listOf("Fatigue chronique", "Asthénie", "Faiblesse générale", "Burn-out", "Manque de vitalité")
    ),
    PathologieInfo(
        nom = "Stress et troubles du sommeil",
        emoji = "😴",
        description = "Stress chronique, anxiété, insomnie affectant l'équilibre mental et physique.",
        symptomes = "Difficultés d'endormissement, réveils nocturnes, irritabilité, tension musculaire, palpitations.",
        risquesSiNeglige = "Dépression, maladies cardiovasculaires, obésité, baisse des défenses immunitaires.",
        produitsRefs = listOf("439", "207", "15"),
        pathologiesSimilaires = listOf("Stress", "Anxiété", "Insomnie", "Troubles du sommeil", "Dépression légère", "Nervosité")
    ),
    PathologieInfo(
        nom = "Maux de tête et migraines",
        emoji = "🤕",
        description = "Céphalées et migraines récurrentes, souvent invalidantes.",
        symptomes = "Douleurs crâniennes pulsatiles, nausées, sensibilité à la lumière et au bruit, vision trouble.",
        risquesSiNeglige = "Chronicisation, dépendance aux antalgiques, impact majeur sur la qualité de vie.",
        produitsRefs = listOf("439", "15"),
        pathologiesSimilaires = listOf("Migraine", "Céphalées", "Maux de tête chroniques", "Céphalées de tension")
    ),
    PathologieInfo(
        nom = "Troubles ORL et allergies",
        emoji = "🤧",
        description = "Sinusite, rhinites allergiques, allergies saisonnières.",
        symptomes = "Nez bouché, éternuements, écoulements nasaux, maux de gorge, toux, démangeaisons oculaires.",
        risquesSiNeglige = "Infections chroniques, asthme, polypes nasaux, perte olfactive.",
        produitsRefs = listOf("27", "48", "15"),
        pathologiesSimilaires = listOf("Sinusite", "Rhinite allergique", "Allergies saisonnières", "Asthme", "Toux chronique", "Angine")
    ),
    PathologieInfo(
        nom = "Problèmes de peau",
        emoji = "✨",
        description = "Acné, eczéma, psoriasis et autres affections cutanées.",
        symptomes = "Boutons, plaques rouges, démangeaisons, peau sèche ou grasse, cicatrices.",
        risquesSiNeglige = "Infections cutanées secondaires, cicatrices permanentes, impact psychologique.",
        produitsRefs = listOf("15", "51", "205", "40"),
        pathologiesSimilaires = listOf("Acné", "Eczéma", "Psoriasis", "Dermatite", "Peau sèche", "Hyperpigmentation", "Vergetures")
    ),
    PathologieInfo(
        nom = "Chute de cheveux",
        emoji = "💆",
        description = "Alopécie, chute excessive des cheveux d'origine diverse.",
        symptomes = "Perte de cheveux en grande quantité, clairsemement, calvitie progressive.",
        risquesSiNeglige = "Calvitie définitive, impact sur la confiance en soi.",
        produitsRefs = listOf("640", "641", "439"),
        pathologiesSimilaires = listOf("Alopécie", "Calvitie", "Chute de cheveux saisonnière", "Pelade", "Cheveux cassants")
    ),
    PathologieInfo(
        nom = "Anémie",
        emoji = "🩸",
        description = "Diminution du taux d'hémoglobine dans le sang.",
        symptomes = "Fatigue, pâleur, essoufflement, vertiges, palpitations, ongles cassants.",
        risquesSiNeglige = "Insuffisance cardiaque, complications pendant la grossesse, retard de développement chez l'enfant.",
        produitsRefs = listOf("439", "207"),
        pathologiesSimilaires = listOf("Anémie ferriprive", "Anémie par carence en B12", "Anémie falciforme", "Fatigue liée à l'anémie")
    ),
    PathologieInfo(
        nom = "Renforcement du système immunitaire",
        emoji = "🛡️",
        description = "Faible immunité, infections répétées, défenses naturelles insuffisantes.",
        symptomes = "Infections fréquentes, fatigue chronique, lente guérison des blessures, sensibilité aux maladies.",
        risquesSiNeglige = "Maladies chroniques, infections graves, terrain favorable aux cancers.",
        produitsRefs = listOf("439", "27", "48", "36", "355", "72"),
        pathologiesSimilaires = listOf("Faible immunité", "Infections répétées", "Rhumes fréquents", "Convalescence", "Post-COVID")
    ),
    PathologieInfo(
        nom = "Détoxification et santé du foie",
        emoji = "🍃",
        description = "Surcharge hépatique, toxines accumulées, foie fatigué.",
        symptomes = "Fatigue, teint terne, ballonnements, digestion difficile, mauvaise haleine, maux de tête.",
        risquesSiNeglige = "Stéatose hépatique, cirrhose, insuffisance hépatique.",
        produitsRefs = listOf("15", "464", "68", "72", "355"),
        pathologiesSimilaires = listOf("Stéatose hépatique", "Foie gras", "Intoxication", "Surcharge hépatique", "Jaunisse")
    ),
    PathologieInfo(
        nom = "Santé des reins",
        emoji = "🫘",
        description = "Infections rénales, calculs rénaux, insuffisance rénale débutante.",
        symptomes = "Douleurs lombaires, urines foncées, gonflement des chevilles, fatigue, hypertension.",
        risquesSiNeglige = "Insuffisance rénale chronique, dialyse, greffe rénale nécessaire.",
        produitsRefs = listOf("15", "439", "34"),
        pathologiesSimilaires = listOf("Calculs rénaux", "Insuffisance rénale", "Néphrite", "Infection rénale", "Pyélonéphrite")
    )
)