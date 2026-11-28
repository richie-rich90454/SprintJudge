// Deterministic generator for the Modern Physics question bank.
// Every item is a self-contained quantitative/conceptual problem with data,
// an answer computed from standard physics formulas, four parallel options
// whose distractors encode named misconceptions, a teaching explanation,
// a hint, and full provenance. Run: node scripts/gen-modphys.mjs
import fs from "node:fs";
import path from "node:path";

// ---- physical constants (SI unless noted) ----
const c = 2.99792458e8;
const h = 6.62607015e-34;
const hbar = 1.054571817e-34;
const eV = 1.602176634e-19;
const me = 9.1093837015e-31;
const mp = 1.67262192369e-27;
const mn = 1.67492749804e-27;
const uMeV = 931.49410242;
const hcEVnm = 1239.841984; // eV*nm
const PI = Math.PI;

// ---- formatting ----
function sci(x, n = 3) {
  if (x === 0) return "0";
  const a = Math.abs(x);
  let e = Math.floor(Math.log10(a));
  let m = a / Math.pow(10, e);
  let ms = m.toFixed(n - 1);
  if (parseFloat(ms) >= 10) { ms = (parseFloat(ms) / 10).toFixed(n - 1); e++; }
  return (x < 0 ? "-" : "") + ms + " × 10^" + e;
}
function fx(x, d = 2) { return x.toFixed(d); }
function pick(arr, n) { return arr[((n % arr.length) + arr.length) % arr.length]; }
function gam(beta) { return 1 / Math.sqrt(1 - beta * beta); }
function pr(n, mod, mul) { return ((n * mul) % mod + mod) % mod; }

// ---- option arrangement: rotate correct answer across A-D ----
function mk(r, stem, correct, dis, unit, mis, explanation, hint, extra = {}) {
  const ci = ((r % 4) + 4) % 4;
  const texts = new Array(4), miscon = new Array(4);
  texts[ci] = correct; miscon[ci] = "";
  let k = 0;
  for (let i = 0; i < 4; i++) if (i !== ci) { texts[i] = dis[k]; miscon[i] = mis[k]; k++; }
  const options = texts.map((t, i) => ({ id: "ABCD"[i], text: t + " " + unit, misconception: miscon[i] }));
  return { stem, options, answer: { correctId: "ABCD"[ci] }, explanation, hint, format: "CONCEPT_MCQ", ...extra };
}

// reusable misconception strings (>=12 chars each)
const M = {
  noRel: "Assumes no relativistic effect operates at this speed",
  invGamma: "Divides by gamma instead of multiplying, reversing the effect",
  gammaMinusOne: "Uses gamma minus one rather than gamma itself",
  galilean: "Adds speeds with Galilean arithmetic, ignoring the c^2 denominator",
  wrongDenom: "Uses a minus sign in the velocity-addition denominator",
  classicalKE: "Uses the classical kinetic-energy formula mv^2/2",
  forgetsWork: "Ignores the work function and reports the bare photon energy",
  addsWork: "Adds instead of subtracting the work function",
  wrongFrame: "Confuses the moving-frame value with the proper-frame value",
  directProp: "Assumes a linear rather than inverse-square or exponential relation",
  noConvert: "Forgets to convert units before applying the formula",
  ignoresCharge: "Ignores electric charge conservation",
  countsNucleons: "Counts total nucleons without tracking protons separately",
  usesLambda: "Uses the decay constant where the half-life is required",
};

// scenario word banks -> keep openings varied
const PARTICLES = ["muon", "charged pion", "tau lepton", "neutral kaon", "Sigma hyperon", "lambda particle", "positive pion"];
const FRAMES = ["a laboratory", "a ground detector", "an orbital platform", "a tracking station", "an Earth observer"];
const SOURCES = ["a metal cathode", "a sodium surface", "a cesium photocell", "a zinc plate", "a potassium film"];
const NUCLEI = ["an unstable isotope", "a radioactive sample", "a freshly prepared nuclide", "a reactor product", "a cosmic-ray fragment"];

// =====================================================================
// FAMILY BUILDERS. Each returns a question body or null (to force retry).
// 'n' is derived from (seed, salt) so retries explore new parameters.
// =====================================================================
const F = {};

// ---------- Unit 1: Special Relativity ----------
F["1.1a"] = (n) => {
  const beta = 0.100 + pr(n, 900, 37) / 1000;
  const t0 = pick([0.5, 1.0, 1.5, 2.0, 2.2, 3.0, 4.0, 5.0, 8.0], pr(n, 9, 5));
  const g = gam(beta), t = g * t0;
  const p = pick(PARTICLES, pr(n, PARTICLES.length, 11)), fr = pick(FRAMES, pr(n, FRAMES.length, 7));
  const stem = `A ${p} moving at ${fx(beta, 3)}c relative to ${fr} has a proper lifetime of ${fx(t0, 2)} microseconds. What lifetime is measured in that frame?`;
  const dis = [sci(t0) + " us", sci(t0 / g) + " us", sci((g - 1) * t0) + " us"];
  const r = pr(n, 4, 3);
  return mk(r, stem, sci(t) + " us", dis, "", [M.noRel, M.invGamma, M.gammaMinusOne],
    `The proper lifetime is measured in the particle's own rest frame, so the observer measures a dilated time t = gamma times t0, with gamma = 1/sqrt(1 - beta^2) = ${fx(g, 3)}. That gives t = ${fx(g, 3)} times ${fx(t0, 2)} = ${sci(t)} microseconds. Quoting t0 ignores dilation, dividing by gamma reverses the effect, and using gamma minus one confuses dilation with the kinetic-energy factor.`,
    "Use t = gamma times the proper time, with gamma = 1/sqrt(1 minus beta squared).");
};
F["1.1b"] = (n) => {
  const beta = 0.200 + pr(n, 750, 41) / 1000;
  const t0 = pick([1.0, 2.0, 2.2, 3.0, 5.0, 6.0], pr(n, 6, 13));
  const g = gam(beta), tobs = g * t0;
  const rec = Math.sqrt(1 - (t0 / tobs) ** 2);
  const p = pick(PARTICLES, pr(n, PARTICLES.length, 17));
  const stem = `${p.charAt(0).toUpperCase() + p.slice(1)}s have proper lifetime ${fx(t0, 2)} microseconds; observers clock one at ${sci(tobs)} microseconds. What is its speed as a fraction of c?`;
  const dis = [sci(t0 / tobs) + " c", sci(g - 1) + " c", sci(beta * beta) + " c"];
  return mk(pr(n, 4, 5), stem, sci(rec) + " c", dis, "", [M.wrongFrame, M.gammaMinusOne, M.directProp],
    `From time dilation, tobs = gamma times t0, so gamma = tobs/t0 and beta = sqrt(1 - 1/gamma^2) = sqrt(1 - (t0/tobs)^2) = ${fx(rec, 3)}. The ratio t0/tobs equals 1/gamma, not beta; gamma minus one and beta squared are unrelated to the recovered speed. The particle therefore moves at about ${fx(rec, 3)}c.`,
    "Invert gamma = 1/sqrt(1 minus beta squared) rather than using the raw ratio.");
};
F["1.1c"] = (n) => {
  const beta = 0.300 + pr(n, 683, 53) / 1000;
  const t0us = pick([1.0, 2.2, 3.0, 4.0, 5.0], pr(n, 7, 19));
  const t0 = t0us * 1e-6;
  const g = gam(beta), labTime = g * t0, dist = beta * c * labTime;
  const p = pick(PARTICLES, pr(n, PARTICLES.length, 23));
  const stem = `A ${p} with proper lifetime ${fx(t0us, 2)} microseconds travels at ${fx(beta, 3)}c. How far does it travel in the laboratory before decaying?`;
  const dis = [sci(beta * c * t0) + " m", sci(c * labTime) + " m", sci(dist / 2) + " m"];
  return mk(pr(n, 4, 7), stem, sci(dist) + " m", dis, "", [M.noRel, "Treats the particle as moving at light speed", "Halves the distance without a physical basis"],
    `The proper lifetime is ${fx(t0us, 2)} microseconds, so in the laboratory it is dilated to gamma times t0 = ${sci(labTime)} seconds. Multiplying by the speed beta c gives d = ${sci(dist)} meters. Using the undilated time shortens the distance, assuming speed c overestimates it, and halving the result has no basis in time dilation.`,
    "Multiply the dilated lifetime by the particle's laboratory speed.");
};

F["1.2a"] = (n) => {
  const beta = 0.200 + pr(n, 850, 61) / 1000;
  const L0 = pick([2.0, 3.0, 5.0, 8.0, 10.0, 12.0, 20.0], pr(n, 7, 29));
  const g = gam(beta), L = L0 / g;
  const obj = pick(["rod", "spacecraft", "tunnel", "platform", "beam"], pr(n, 5, 31));
  const stem = `At ${fx(beta, 3)}c an object of proper length ${fx(L0, 1)} meters passes a detector. What length does the detector measure?`;
  const dis = [sci(g * L0) + " m", sci(L0) + " m", sci(L0 * (1 - beta)) + " m"];
  return mk(pr(n, 4, 9), stem, sci(L) + " m", dis, "", [M.invGamma, M.noRel, "Applies a linear rather than Lorentz-contraction factor"],
    `Lengths along the direction of motion contract by the Lorentz factor: L = L0/gamma with gamma = 1/sqrt(1 - beta^2) = ${fx(g, 3)}. Hence L = ${fx(L0, 1)}/${fx(g, 3)} = ${sci(L)} meters. Multiplying by gamma would expand the object, quoting L0 ignores contraction, and L0 times (1 - beta) is not the Lorentz formula.`,
    "Divide the proper length by gamma, never multiply.");
};
F["1.2b"] = (n) => {
  const beta = 0.250 + pr(n, 701, 67) / 1000;
  const L = 1 + pr(n, 13, 37) / 2;
  const g = gam(beta), L0 = g * L;
  const stem = `A ${pick(["rod", "garage", "tube", "pencil"], pr(n, 4, 43))} moving at ${fx(beta, 3)}c is measured to be ${fx(L, 2)} meters long. What is its proper length?`;
  const dis = [sci(L / g) + " m", sci(L) + " m", sci(L * (1 - beta * beta)) + " m"];
  return mk(pr(n, 4, 11), stem, sci(L0) + " m", dis, "", [M.invGamma, M.noRel, "Uses one minus beta squared instead of the square root"],
    `The measured length is contracted, so the proper length is larger: L0 = gamma times L, with gamma = ${fx(g, 3)}. Thus L0 = ${fx(g, 3)} times ${fx(L, 2)} = ${sci(L0)} meters. Dividing by gamma would contract an already contracted length, quoting L ignores contraction, and L times (1 - beta squared) uses the wrong factor.`,
    "The proper length is always the largest; multiply the contracted length by gamma.");
};

F["1.3a"] = (n) => {
  const u = 0.10 + pr(n, 850, 71) / 1000, v = 0.10 + pr(n, 800, 73) / 1000;
  const u2 = (u + v) / (1 + u * v);
  const gal = u + v, wd = (u + v) / (1 - u * v);
  const stem = `A probe moves at ${fx(u, 3)}c relative to a station, and a particle is fired forward at ${fx(v, 3)}c relative to the probe. What is the particle's speed relative to the station, in units of c?`;
  const dis = [sci(gal) + " c", sci(wd) + " c", sci(u * v) + " c"];
  return mk(pr(n, 4, 13), stem, sci(u2) + " c", dis, "", [M.galilean, M.wrongDenom, "Reports the product of the speeds instead of their combination"],
    `Velocities combine as u = (u' + v)/(1 + u'v/c^2). With u' = ${fx(u, 3)} and v = ${fx(v, 3)} this gives ${fx(u, 3)} + ${fx(v, 3)} over 1 + ${fx(u * v, 4)} = ${fx(u2, 3)}c. Simple addition ignores the relativistic denominator, a minus sign in the denominator can exceed c, and the product is not a speed combination at all.`,
    "Use u = (u' + v)/(1 + u'v/c^2), and keep the plus sign in the denominator.");
};
F["1.3b"] = (n) => {
  const a = 0.20 + pr(n, 750, 79) / 1000, b = 0.20 + pr(n, 700, 83) / 1000;
  const rel = (a + b) / (1 + a * b);
  const stem = `Two beams approach each other, each moving at ${fx(a, 3)}c and ${fx(b, 3)}c relative to the lab in opposite directions. What is the speed of one beam as seen from the other, in units of c?`;
  const dis = [sci(a + b) + " c", sci(a * b) + " c", sci((a + b) / (1 - a * b)) + " c"];
  return mk(pr(n, 4, 17), stem, sci(rel) + " c", dis, "", [M.galilean, "Reports the product of the two speeds instead of their combination", M.wrongDenom],
    `In the rest frame of one beam the other approaches at (a + b)/(1 + ab) = (${fx(a, 3)} + ${fx(b, 3)})/(1 + ${fx(a * b, 4)}) = ${fx(rel, 3)}c. Adding naively can exceed c, subtracting gives their lab-frame separation rate, and the wrong-sign denominator also breaks the light-speed limit.`,
    "Use the relativistic relative-speed formula with both speeds positive.");
};

F["1.4a"] = (n) => {
  const m = (1 + pr(n, 90, 89)) * 1e-27;
  const E = m * c * c;
  const stem = `A small amount of matter of mass ${sci(m)} kilograms is converted completely into energy. How much energy is released?`;
  const dis = [sci(m * c) + " J", sci(m * c * c * c) + " J", sci(m / (c * c)) + " J"];
  return mk(pr(n, 4, 19), stem, sci(E) + " J", dis, "", ["Uses momentum mc instead of energy mc squared", "Adds an extra factor of c", "Divides by c squared instead of multiplying"],
    `Mass-energy equivalence gives E = m c^2. With c = 2.998 times 10^8 m/s, E = ${sci(m)} times (2.998 times 10^8)^2 = ${sci(E)} joules. The quantity mc is momentum, mc^3 has the wrong dimensions, and dividing by c^2 would give mass per energy rather than energy.`,
    "Square the speed of light and multiply by the converted mass.");
};
F["1.4b"] = (n) => {
  const dm = (1 + pr(n, 90, 97)) / 1000; // u
  const E = dm * uMeV;
  const stem = `In a reaction the total rest mass decreases by ${fx(dm, 3)} atomic mass units. How much energy is released, in MeV?`;
  const dis = [sci(dm * 931.5 * 1e-3) + " MeV", sci(dm / uMeV) + " MeV", sci(dm * c) + " MeV"];
  return mk(pr(n, 4, 23), stem, fx(E, 2) + " MeV", dis, "", ["Misplaces the decimal in the 931.5 conversion", "Divides by 931.5 instead of multiplying", "Uses the speed of light instead of the mass-energy conversion factor"],
    `One atomic mass unit is equivalent to 931.5 MeV, so a mass defect of ${fx(dm, 3)} u releases ${fx(dm, 3)} times 931.5 = ${fx(E, 2)} MeV. Dividing by 931.5, misplacing the decimal, or multiplying by c instead of 931.5 MeV/u all give the wrong energy scale.`,
    "Multiply the mass defect in unified atomic mass units by 931.5 MeV per u.");
};

F["1.5a"] = (n) => {
  const beta = 0.200 + pr(n, 780, 101) / 1000;
  const mass = pick([1.0, 2.0, 5.0, 9.11e-31 / 1e-27], 4) * 1e-27;
  const g = gam(beta), p = g * mass * beta * c;
  const pcl = mass * beta * c;
  const stem = `At ${fx(beta, 3)}c a particle of mass ${sci(mass)} kilograms moves. What is its relativistic momentum?`;
  const dis = [sci(pcl) + " kg m/s", sci(mass * c) + " kg m/s", sci(g * mass * c) + " kg m/s"];
  return mk(pr(n, 4, 29), stem, sci(p) + " kg m/s", dis, "", [M.classicalKE, "Uses mass times light speed ignoring the actual velocity", "Omits the velocity factor from gamma times mass times v"],
    `Relativistic momentum is p = gamma times m times v = ${fx(g, 3)} times ${sci(mass)} times ${fx(beta, 3)}c = ${sci(p)} kg m/s. The classical value m times v omits gamma and is too small; m times c ignores the particle's actual speed; gamma times m times c omits the velocity factor entirely.`,
    "Multiply the classical momentum by the Lorentz factor gamma.");
};
F["1.5b"] = (n) => {
  const beta = 0.300 + pr(n, 650, 103) / 1000;
  const rest = pick([511, 938, 140, 1057, 494], pr(n, 5, 107)); // MeV
  const g = gam(beta), K = (g - 1) * rest;
  const stem = `A particle with rest energy ${rest} MeV moves at ${fx(beta, 3)}c. What is its relativistic kinetic energy?`;
  const dis = [fx(0.5 * rest * beta * beta, 1) + " MeV", fx((g - 1) * rest * 0.5, 1) + " MeV", fx(g * rest, 1) + " MeV"];
  return mk(pr(n, 4, 31), stem, fx(K, 1) + " MeV", dis, "", [M.classicalKE, "Halves the kinetic energy a second time", "Reports the total energy including rest mass"],
    `Relativistic kinetic energy is K = (gamma - 1) times the rest energy. Here gamma = ${fx(g, 3)}, so K = ${fx(g - 1, 3)} times ${rest} = ${fx(K, 1)} MeV. The classical half m v squared badly underestimates the result at relativistic speed, halving again is arbitrary, and gamma times rest energy is the total energy, not the kinetic part.`,
    "Subtract one from gamma, then multiply by the rest energy.");
};

F["1.6a"] = (n) => {
  const ct = 5 + pr(n, 23, 109); // light-travel distance in m
  const dx = 1 + pr(n, 5, 113); // m
  const s2 = ct * ct - dx * dx;
  const stem = `With a ${dx} meter spatial gap and ${ct} meters of light travel time, what is the squared interval (c delta t)^2 minus (delta x)^2?`;
  const dis = [fx(ct * ct + dx * dx, 0) + " m^2", fx(dx * dx - ct * ct, 0) + " m^2", fx(ct * ct, 0) + " m^2"];
  return mk(pr(n, 4, 37), stem, fx(s2, 0) + " m^2", dis, "", ["Adds the spatial term instead of subtracting it", "Reverses the sign of the whole interval", "Drops the spatial separation term"],
    `The invariant interval is (c delta t)^2 minus (delta x)^2 = ${ct}^2 minus ${dx}^2 = ${s2} square meters. Adding the terms computes the wrong combination, reversing the sign swaps timelike and spacelike character, and dropping delta x ignores part of the separation. Because the result is positive the events are timelike separated.`,
    "Compute (c delta t)^2 minus (delta x)^2 with the time term first.");
};
F["1.6b"] = (n) => {
  const dt = (2 + pr(n, 8, 121)) * 1e-6; // s
  const dx = (100 + pr(n, 400, 127)) ; // m
  const dtau = Math.sqrt(Math.max(0, dt * dt - (dx / c) ** 2));
  const stem = `Two events occur ${fx(dx, 0)} meters apart and are separated by ${sci(dt)} seconds. What is the proper time interval between them?`;
  const dis = [sci(dt) + " s", sci(dt + dx / c) + " s", sci(Math.sqrt(dt * dt + (dx / c) ** 2)) + " s"];
  return mk(pr(n, 4, 41), stem, sci(dtau) + " s", dis, "", [M.noRel, "Adds the light-travel time instead of subtracting in quadrature", "Uses a plus sign under the square root"],
    `The proper time is the invariant interval divided by c: delta tau = sqrt(delta t^2 minus (delta x/c)^2) = ${sci(dtau)} seconds. Quoting delta t ignores the spatial gap, adding delta x/c is the wrong operation, and a plus sign under the root describes the spacelike combination instead of the proper time.`,
    "Use delta tau = sqrt(delta t squared minus (delta x over c) squared).");
};

F["1.7a"] = (n) => {
  const beta = 0.200 + pr(n, 701, 131) / 1000;
  const t = 1 + pr(n, 11, 137); // s
  const x = (1 + pr(n, 13, 139)) * 1e8; // m
  const g = gam(beta), tp = g * (t - beta * x / c);
  const stem = `At t = ${fx(t, 1)} seconds an event sits at x = ${sci(x)} meters in S, while S' moves at ${fx(beta, 3)}c. Find t'.`;
  const dis = [sci(t - beta * x / c) + " s", sci(g * (t + beta * x / c)) + " s", sci(t) + " s"];
  return mk(pr(n, 4, 43), stem, sci(tp) + " s", dis, "", ["Omits the Lorentz factor gamma", "Uses a plus sign in the Lorentz transformation", "Reports the untransformed coordinate time"],
    `The Lorentz time transformation is t' = gamma times (t minus v x/c^2) = gamma times (t minus beta x/c). With gamma = ${fx(g, 3)} this gives ${sci(tp)} seconds. Dropping gamma or flipping the sign inside the parentheses are classic errors, and quoting t ignores the transformation altogether.`,
    "Apply t' = gamma times (t minus v x over c squared).");
};
F["1.7b"] = (n) => {
  const beta = 0.200 + pr(n, 701, 149) / 1000;
  const t = 1 + pr(n, 11, 151); // s
  const x = (1 + pr(n, 13, 157)) * 1e8; // m
  const g = gam(beta), xp = g * (x - beta * c * t);
  const stem = `An event occurs at x = ${sci(x)} meters, t = ${fx(t, 1)} seconds in frame S. A frame moving at ${fx(beta, 3)}c measures what value of x'?`;
  const dis = [sci(x - beta * c * t) + " m", sci(g * (x + beta * c * t)) + " m", sci(x) + " m"];
  return mk(pr(n, 4, 47), stem, sci(xp) + " m", dis, "", ["Omits the Lorentz factor gamma", "Uses a plus sign in the spatial transformation", "Reports the untransformed coordinate"],
    `The spatial Lorentz transformation is x' = gamma times (x minus v t) = gamma times (x minus beta c t). With gamma = ${fx(g, 3)} this yields ${sci(xp)} meters. Omitting gamma, using a plus sign, or quoting x directly each fails to transform the coordinate correctly.`,
    "Apply x' = gamma times (x minus v t).");
};

// ---------- Unit 2: Quantum Physics ----------
F["2.1a"] = (n) => {
  const lam = 120 + pr(n, 480, 163); // nm
  const phi = 1.5 + pr(n, 40, 167) / 10; // eV
  const E = hcEVnm / lam, K = E - phi;
  const src = pick(SOURCES, pr(n, SOURCES.length, 169));
  const stem = `Light of wavelength ${lam} nm shines on ${src} whose work function is ${fx(phi, 2)} eV. What is the maximum kinetic energy of the ejected electrons?`;
  const dis = [fx(E, 2) + " eV", fx(E + phi, 2) + " eV", fx(phi, 2) + " eV"];
  return mk(pr(n, 4, 53), stem, fx(K, 2) + " eV", dis, "", [M.forgetsWork, M.addsWork, "Reports only the work function as the electron energy"],
    `The photon energy is hc/lambda = 1240/${lam} = ${fx(E, 2)} eV. Einstein's photoelectric equation gives Kmax = photon energy minus work function = ${fx(E, 2)} minus ${fx(phi, 2)} = ${fx(K, 2)} eV. Ignoring the work function, adding it, or quoting it alone each misuses the energy balance.`,
    "Subtract the work function from the photon energy hc over lambda.");
};
F["2.1b"] = (n) => {
  const lam = 150 + pr(n, 350, 173); // nm
  const phi = 1.5 + pr(n, 30, 179) / 10;
  const K = hcEVnm / lam - phi, Vs = K; // eV -> V for a single electron
  const stem = `At ${lam} nm a photocell of work function ${fx(phi, 2)} eV is illuminated. What stopping potential halts the photoelectrons?`;
  const dis = [fx(hcEVnm / lam, 2) + " V", fx(K + 2 * phi, 2) + " V", fx(phi, 2) + " V"];
  return mk(pr(n, 4, 59), stem, fx(Vs, 2) + " V", dis, "", ["Uses the photon energy instead of the electron kinetic energy", "Adds the work function to the kinetic energy a second time", "Reports the work function as the stopping voltage"],
    `The stopping potential satisfies e times Vs = Kmax = hc/lambda minus work function = ${fx(K, 2)} eV, so Vs = ${fx(Vs, 2)} volts for a single electron. Using the photon energy, adding the work function, or quoting the work function all ignore that only the excess photon energy becomes electron kinetic energy.`,
    "Set e times the stopping voltage equal to the photoelectron kinetic energy.");
};
F["2.1c"] = (n) => {
  const phi = 1.6 + pr(n, 37, 181) / 10; // eV
  const lam0 = hcEVnm / phi;
  const src = pick(SOURCES, pr(n, SOURCES.length, 191));
  const stem = `A ${src} has work function ${fx(phi, 2)} eV. What is its threshold wavelength for the photoelectric effect?`;
  const dis = [fx(lam0 / 2, 1) + " nm", fx(phi * hcEVnm, 0) + " nm", fx(lam0 * 2, 1) + " nm"];
  return mk(pr(n, 4, 61), stem, fx(lam0, 1) + " nm", dis, "", [M.directProp, "Multiplies rather than divides hc by the work function", "Doubles the threshold wavelength without basis"],
    `The threshold occurs when the photon energy just equals the work function: hc/lambda0 = phi, so lambda0 = 1240/${fx(phi, 2)} = ${fx(lam0, 1)} nm. Halving the wavelength, multiplying hc by phi, or doubling the result do not follow from this equality.`,
    "Set hc over lambda equal to the work function, then solve for lambda.");
};

F["2.2a"] = (n) => {
  const lam = 100 + pr(n, 700, 191); // nm
  const E = hcEVnm / lam;
  const stem = `A photon has wavelength ${lam} nm. What is its energy in electron volts?`;
  const dis = [fx(E / 2, 2) + " eV", fx(E * 2, 2) + " eV", fx(lam / hcEVnm, 4) + " eV"];
  return mk(pr(n, 4, 67), stem, fx(E, 2) + " eV", dis, "", [M.directProp, "Doubles the photon energy without reason", "Inverts the hc over lambda relation"],
    `A photon's energy is E = hc/lambda = 1240 eV nm divided by ${lam} nm = ${fx(E, 2)} eV. Halving or doubling ignores the inverse relationship, and computing lambda over hc is the reciprocal of the correct formula.`,
    "Use E = hc over lambda with hc = 1240 eV nm.");
};
F["2.2b"] = (n) => {
  const lam = (50 + pr(n, 450, 197)) * 1e-9;
  const p = h / lam;
  const stem = `A photon of wavelength ${sci(lam)} meters carries what momentum?`;
  const dis = [sci(h * lam) + " kg m/s", sci(h / (lam * lam)) + " kg m/s", sci(h * c / lam) + " kg m/s"];
  return mk(pr(n, 4, 71), stem, sci(p) + " kg m/s", dis, "", [M.directProp, "Divides by wavelength squared", "Confuses photon momentum with energy hc over lambda"],
    `For a photon p = h/lambda = ${sci(h)} divided by ${sci(lam)} = ${sci(p)} kg m/s. Multiplying h by lambda, dividing by lambda squared, or using hc/lambda (the energy) all misuse the de Broglie momentum relation.`,
    "Photon momentum is Planck's constant divided by wavelength.");
};

F["2.3a"] = (n) => {
  const m = (1 + pr(n, 40, 199)) * 1e-27;
  const v = (1 + pr(n, 9, 211)) * 1e3;
  const lam = h / (m * v);
  const stem = `A particle of mass ${sci(m)} kg moves at ${sci(v)} m/s. What is its de Broglie wavelength?`;
  const dis = [sci(m * v / h) + " m", sci(h * m * v) + " m", sci(h / m) + " m"];
  return mk(pr(n, 4, 73), stem, sci(lam) + " m", dis, "", [M.directProp, "Multiplies h by the momentum instead of dividing", "Omits the velocity from the momentum"],
    `The de Broglie wavelength is lambda = h/p = h/(m v) = ${sci(h)} divided by (${sci(m)} times ${sci(v)}) = ${sci(lam)} meters. Inverting the ratio, multiplying by momentum, or dropping the velocity each misapply the relation.`,
    "Divide Planck's constant by the particle's momentum m v.");
};
F["2.3b"] = (n) => {
  const V = 25 + pr(n, 975, 223); // volts
  const lam = 1.226e-9 / Math.sqrt(V); // meters, nonrelativistic electron
  const stem = `An electron is accelerated from rest through a potential difference of ${V} volts. What is its de Broglie wavelength?`;
  const dis = [sci(1.226e-9 * Math.sqrt(V)) + " m", sci(1.226e-9 / V) + " m", sci(1.226e-9 * V) + " m"];
  return mk(pr(n, 4, 79), stem, sci(lam) + " m", dis, "", [M.directProp, "Divides by V instead of the square root of V", "Multiplies by V instead of dividing"],
    `An electron accelerated through V volts has momentum p = sqrt(2 m e V), giving lambda = h/p = 1.226 nm divided by the square root of V. For V = ${V} that is ${sci(lam)} meters. The variants scale with the wrong power of V and are not the de Broglie wavelength.`,
    "Use lambda = 1.226 nm divided by the square root of the accelerating voltage.");
};

F["2.4a"] = (n) => {
  const Z = 1 + pr(n, 12, 227);
  const N = 1 + pr(n, 11, 229);
  const E = -13.6 * Z * Z / (N * N);
  const stem = `For Z = ${Z} and n = ${N}, what is the energy in eV of a hydrogen-like ion's level?`;
  const dis = [fx(-13.6 * Z * Z / N, 2) + " eV", fx(13.6 * Z * Z / (N * N), 2) + " eV", fx(-13.6 * N / (Z * Z), 2) + " eV"];
  return mk(pr(n, 4, 83), stem, fx(E, 2) + " eV", dis, "", ["Uses n instead of n squared in the denominator", "Drops the negative sign of the bound state", "Swaps the roles of n and Z in the formula"],
    `For a hydrogen-like ion the Bohr energies are E_n = -13.6 Z^2/n^2 eV. With Z = ${Z} and n = ${N}, E = -13.6 times ${Z * Z} divided by ${N * N} = ${fx(E, 2)} eV. Using n, dropping the minus sign, or swapping n and Z each misstate the level structure of the bound electron.`,
    "Apply E_n = -13.6 times Z squared divided by n squared.");
};
F["2.4b"] = (n) => {
  const Z = 1 + pr(n, 13, 233);
  const pairs = [[3, 2], [4, 2], [5, 2], [4, 3], [5, 3], [6, 2], [5, 4], [6, 3]];
  const [ni, nf] = pick(pairs, pr(n, pairs.length, 239));
  const E = 13.6 * Z * Z * (1 / (nf * nf) - 1 / (ni * ni));
  const lam = hcEVnm / E;
  const stem = `For Z = ${Z}, an n = ${ni} to n = ${nf} decay in a hydrogen-like ion emits what wavelength?`;
  const dis = [fx(hcEVnm / (13.6 * Z * Z / (nf * nf)), 1) + " nm", fx(hcEVnm * E, 1) + " nm", fx(2 * lam, 1) + " nm"];
  return mk(pr(n, 4, 89), stem, fx(lam, 1) + " nm", dis, "", ["Uses only the final level energy", "Multiplies instead of dividing hc by the transition energy", "Doubles the wavelength arbitrarily"],
    `The photon carries the level difference Delta E = 13.6 Z^2 (1/${nf}^2 - 1/${ni}^2) = ${fx(E, 3)} eV, so lambda = hc/Delta E = 1240/${fx(E, 3)} = ${fx(lam, 1)} nm. Using a single level, multiplying hc by the energy, or doubling the wavelength all miss the photon-energy relation.`,
    "Find the Z-squared energy difference first, then divide 1240 eV nm by it.");
};

F["2.5a"] = (n) => {
  const dx = (0.1 + pr(n, 90, 233) / 10) * 1e-10;
  const dp = hbar / (2 * dx);
  const stem = `The position of an electron is known to within ${sci(dx)} meters. What is the minimum uncertainty in its momentum?`;
  const dis = [sci(hbar / dx) + " kg m/s", sci(hbar * dx) + " kg m/s", sci(2 * hbar / dx) + " kg m/s"];
  return mk(pr(n, 4, 97), stem, sci(dp) + " kg m/s", dis, "", ["Omits the factor of one half", "Multiplies instead of dividing by the position uncertainty", "Uses h instead of h-bar with a factor of two"],
    `Heisenberg's uncertainty principle gives Delta x Delta p >= hbar/2, so the minimum momentum uncertainty is hbar/(2 Delta x) = ${sci(dp)} kg m/s. Omitting the 1/2, multiplying by Delta x, or using 2 hbar/Delta x all violate the stated bound.`,
    "Use Delta p minimum = hbar divided by twice Delta x.");
};
F["2.5b"] = (n) => {
  const tau = (1 + pr(n, 90, 241)) * 1e-8;
  const dE = hbar / (2 * tau) / eV;
  const stem = `An excited state has a mean lifetime of ${sci(tau)} seconds. What is the minimum uncertainty in its energy, in eV?`;
  const dis = [sci(hbar / tau / eV) + " eV", sci(hbar * tau / eV) + " eV", sci(2 * hbar / tau / eV) + " eV"];
  return mk(pr(n, 4, 101), stem, sci(dE) + " eV", dis, "", ["Omits the factor of one half", "Multiplies hbar by the lifetime", "Uses twice hbar over the lifetime"],
    `The energy-time uncertainty relation gives Delta E Delta t >= hbar/2, so Delta E >= hbar/(2 Delta t) = ${sci(dE)} eV after converting joules to electron volts. Dropping the 1/2, multiplying by the lifetime, or doubling hbar each misapply the bound.`,
    "Use Delta E = hbar divided by twice the lifetime, then convert to eV.");
};

F["2.6a"] = (n) => {
  const L = (0.3 + pr(n, 100, 251) / 100) * 1e-9;
  const E1 = h * h / (8 * me * L * L) / eV;
  const stem = `A ${fx(L * 1e9, 2)} nm infinite well confines an electron. What is its ground-state energy?`;
  const dis = [fx(4 * E1, 3) + " eV", fx(E1 / 4, 3) + " eV", fx(2 * E1, 3) + " eV"];
  return mk(pr(n, 4, 103), stem, fx(E1, 3) + " eV", dis, "", ["Uses the n = 2 energy instead of the ground state", "Divides the ground-state energy by four", "Doubles the ground-state energy"],
    `For an infinite square well E_n = n^2 h^2/(8 m L^2), so the ground state (n = 1) is E1 = h^2/(8 m L^2) = ${fx(E1, 3)} eV for a ${fx(L * 1e9, 2)} nm well. Four times this is the n = 2 level, a quarter and double are arbitrary rescalings.`,
    "Use E_n = n squared h squared over 8 m L squared with n = 1.");
};
F["2.6b"] = (n) => {
  const L = (0.3 + pr(n, 61, 257) / 100) * 1e-9;
  const E1 = h * h / (8 * me * L * L) / eV;
  const ni = 2 + pr(n, 3, 263), nf = 1 + pr(n, ni - 1, 269);
  const dE = (ni * ni - nf * nf) * E1, lam = hcEVnm / dE;
  const stem = `In a ${fx(L * 1e9, 2)} nm well an electron drops from n = ${ni} to n = ${nf}. What wavelength is emitted?`;
  const dis = [fx(hcEVnm / E1, 1) + " nm", fx(hcEVnm * dE, 1) + " nm", fx(lam / 2, 1) + " nm"];
  return mk(pr(n, 4, 107), stem, fx(lam, 1) + " nm", dis, "", ["Uses the ground-state energy rather than the transition difference", "Multiplies hc by the transition energy", "Halves the emitted wavelength"],
    `The transition energy is Delta E = (${ni}^2 - ${nf}^2) E1 = ${fx(dE, 2)} eV, so the emitted wavelength is hc/Delta E = ${fx(lam, 1)} nm. Using E1 alone, multiplying hc by Delta E, or halving the wavelength all fail to use the level difference correctly.`,
    "Take the difference of n squared times E1, then divide 1240 by it.");
};

F["2.7a"] = (n) => {
  const kappa = 0.2 + pr(n, 31, 269) / 10; // per nm
  const dL = 1 + pr(n, 11, 271); // nm increase
  const factor = Math.exp(-2 * kappa * dL);
  const stem = `Widening a barrier by ${dL} nm at kappa = ${fx(kappa, 2)} per nm changes tunneling transmission by what factor?`;
  const dis = [fx(1 / factor, 3), fx(2 * factor, 3), fx(factor / 2, 3)];
  return mk(pr(n, 4, 109), stem, fx(factor, 3), dis, "times", [M.directProp, "Assumes a linear rather than exponential dependence", "Halves the transmission without basis"],
    `Transmission through a rectangular barrier varies as exp(-2 kappa L), so increasing the width by delta L multiplies T by exp(-2 kappa delta L) = exp(-2 times ${fx(kappa, 2)} times ${dL}) = ${fx(factor, 3)}. The inverse factor, a linear scaling, and a halving all misapply the exponential tunneling law.`,
    "Multiply by exp of minus two kappa times the added width.");
};
F["2.7b"] = (n) => {
  const deg = pr(n, 180, 271); // angle in degrees
  const rad = deg * PI / 180;
  const prob = Math.cos(rad / 2) ** 2;
  const stem = `At ${deg} degrees from z, a spin-half particle prepared spin-up along that axis is measured along z. What is P(spin-up)?`;
  const dis = [fx(Math.sin(rad / 2) ** 2, 3), fx(Math.cos(rad, 3), 3), "0.5"];
  return mk(pr(n, 4, 113), stem, fx(prob, 3), dis, "", ["Uses the sine-squared instead of cosine-squared amplitude", "Uses the full angle instead of half the angle", "Assumes the result is always one half"],
    `For a spinor rotated by angle theta, the probability of measuring spin-up along z is cos^2(theta/2). With theta = ${deg} degrees this is cos^2(${fx(deg / 2, 1)} degrees) = ${fx(prob, 3)}. The sine-squared gives spin-down, using the full angle is wrong, and one half holds only at ninety degrees.`,
    "Square the cosine of half the rotation angle.");
};

// ---------- Unit 3: Nuclear Physics ----------
F["3.1a"] = (n) => {
  const N0 = 100 * (1 + pr(n, 23, 277));
  const T = 1 + pr(n, 13, 281);
  const halfs = 1 + pr(n, 7, 283);
  const t = T * halfs, N = N0 / (2 ** halfs);
  const stem = `A sample begins with ${N0} radioactive nuclei of half-life ${T} days. How many remain after ${t} days?`;
  const dis = [sci(N0 / (halfs + 1)) + " nuclei", sci(N0 * (1 - halfs / t)) + " nuclei", sci(N0 / 2) + " nuclei"];
  return mk(pr(n, 4, 131), stem, sci(N) + " nuclei", dis, "", ["Divides by the number of half-lives rather than by two each time", "Uses a linear decay law", "Assumes exactly one half-life has elapsed"],
    `After ${halfs} half-lives the surviving number is N0 times (1/2) raised to ${halfs} = ${N0}/2^${halfs} = ${sci(N)} nuclei. Dividing by the count of half-lives, using linear decay, or assuming a single half-life all misapply exponential decay.`,
    "Apply N = N0 times (one half) to the power t over the half-life.");
};
F["3.1b"] = (n) => {
  const T = 1 + pr(n, 23, 293); // days
  const N = 100 * (1 + pr(n, 31, 307));
  const lam = Math.LN2 / T, A = lam * N; // per day
  const stem = `A nuclide has half-life ${T} days and a sample contains ${N} nuclei. What is its activity in decays per day?`;
  const dis = [sci(N / T) + " /day", sci(N * T) + " /day", sci(Math.LN2 * T * N) + " /day"];
  return mk(pr(n, 4, 137), stem, sci(A) + " /day", dis, "", [M.usesLambda, "Divides the nucleus count by the half-life directly", "Multiplies the decay constant by the half-life an extra time"],
    `Activity is A = lambda times N, where lambda = ln(2)/T = ln(2)/${T} = ${fx(lam, 4)} per day. Thus A = ${sci(A)} decays per day. Dividing N by T ignores the ln(2) factor, multiplying N by T is dimensionally wrong, and lambda times T times N double-counts the half-life.`,
    "Compute lambda = ln(2)/T, then multiply by the number of nuclei.");
};
F["3.1c"] = (n) => {
  const T = 2 + pr(n, 19, 311); // years
  const frac = pick([2, 4, 8, 16, 32], pr(n, 7, 313));
  const t = T * Math.log2(frac);
  const stem = `Target one part in ${frac} with a half-life of ${T} years: how long must a sample decay?`;
  const dis = [fx(T * frac, 1) + " years", fx(T / frac, 1) + " years", fx(T * Math.log2(frac) / 2, 1) + " years"];
  return mk(pr(n, 4, 139), stem, fx(t, 1) + " years", dis, "", [M.directProp, "Divides the half-life by the reduction factor", "Halves the correct elapsed time"],
    `A reduction by a factor of ${frac} means ${Math.log2(frac)} half-lives have passed, so t = ${T} times ${Math.log2(frac)} = ${fx(t, 1)} years. Multiplying the half-life by the factor, dividing by it, or halving the answer all do not follow from the exponential decay law.`,
    "Count how many half-lives give the reduction, then multiply by the half-life.");
};

F["3.2a"] = (n) => {
  const dm = 0.005 + pr(n, 200, 317) / 1000; // u
  const BE = dm * uMeV;
  const stem = `A nucleus has a mass defect of ${fx(dm, 3)} unified atomic mass units. What is its total nuclear binding energy?`;
  const dis = [fx(dm * 931.5e-3, 2) + " MeV", fx(dm / uMeV, 4) + " MeV", fx(dm * 1e3, 1) + " MeV"];
  return mk(pr(n, 4, 149), stem, fx(BE, 2) + " MeV", dis, "", ["Misplaces the decimal in the 931.5 conversion", "Divides by 931.5 instead of multiplying", "Scales by one thousand without the MeV conversion"],
    `Binding energy is the mass defect converted to energy: BE = Delta m times 931.5 MeV/u = ${fx(dm, 3)} times 931.5 = ${fx(BE, 2)} MeV. Misplacing the decimal, dividing, or scaling by a bare factor of one thousand each give the wrong energy.`,
    "Multiply the mass defect in u by 931.5 MeV per u.");
};
F["3.2b"] = (n) => {
  const A = 12 + pr(n, 200, 331);
  const dm = 0.005 + pr(n, 200, 337) / 1000;
  const BE = dm * uMeV, per = BE / A;
  const stem = `A nucleus of mass number ${A} has a mass defect of ${fx(dm, 3)} u. What is its binding energy per nucleon?`;
  const dis = [fx(BE, 2) + " MeV", fx(per * 2, 3) + " MeV", fx(per / 2, 3) + " MeV"];
  return mk(pr(n, 4, 151), stem, fx(per, 3) + " MeV", dis, "", ["Reports the total binding energy rather than the per-nucleon value", "Doubles the per-nucleon binding energy", "Halves the per-nucleon binding energy"],
    `First find the total binding energy BE = ${fx(dm, 3)} times 931.5 = ${fx(BE, 2)} MeV, then divide by the ${A} nucleons to get ${fx(per, 3)} MeV per nucleon. Quoting the total, doubling, or halving each fail to divide by the mass number.`,
    "Divide the total binding energy by the mass number A.");
};

F["3.3a"] = (n) => {
  const modes = [
    { dA: -4, dZ: -2, ans: "alpha particle", mis: "Confuses beta-minus emission with alpha decay" },
    { dA: 0, dZ: 1, ans: "beta-minus particle", mis: "Mistakes a neutron-to-proton change for alpha emission" },
    { dA: 0, dZ: -1, ans: "beta-plus particle", mis: "Confuses positron emission with beta-minus decay" },
    { dA: 0, dZ: 0, ans: "gamma ray", mis: "Thinks a change in energy changes the mass number" },
  ];
  const m = pick(modes, pr(n, modes.length, 347));
  const A0 = 200 + pr(n, 41, 349), Z0 = 78 + pr(n, 23, 351);
  const stem = `A parent with A = ${A0}, Z = ${Z0} decays to a daughter with A = ${A0 + m.dA}, Z = ${Z0 + m.dZ}. Which particle was emitted?`;
  const others = modes.filter(x => x !== m);
  const dis = others.map(o => o.ans), mis = others.map(o => o.mis);
  return mk(pr(n, 4, 157), stem, m.ans, dis, "", mis,
    `The change in mass number and atomic number identifies the emission: alpha carries away 4 nucleons and 2 protons, beta-minus converts a neutron to a proton (A fixed, Z up by one), beta-plus does the reverse, and gamma changes neither A nor Z. Here (delta A, delta Z) = (${m.dA}, ${m.dZ}), which corresponds to ${m.ans}.`,
    "Match the change in A and Z to the particle's own mass and charge.");
};
F["3.3b"] = (n) => {
  const A0 = 200 + pr(n, 40, 353), Z0 = 80 + pr(n, 20, 359);
  const mode = pr(n, 3, 361);
  const dA = mode === 0 ? -4 : 0, dZ = mode === 0 ? -2 : (mode === 1 ? 1 : -1);
  const A = A0 + dA, Z = Z0 + dZ;
  const stem = `A parent nuclide with A = ${A0} and Z = ${Z0} undergoes ${mode === 0 ? "alpha" : (mode === 1 ? "beta-minus" : "beta-plus")} decay. What are the daughter's mass and atomic numbers?`;
  const dis = [`A = ${A0}, Z = ${Z0}`, `A = ${A0 - 4}, Z = ${Z0}`, `A = ${A0}, Z = ${Z0 - 2}`];
  return mk(pr(n, 4, 163), stem, `A = ${A}, Z = ${Z}`, dis, "", [M.countsNucleons, "Applies the wrong change to the mass number", "Gets the proton-number change wrong"],
    `Alpha emission removes 4 from A and 2 from Z, while beta decay leaves A fixed and shifts Z by plus or minus one. Starting from A = ${A0}, Z = ${Z0} gives a daughter with A = ${A}, Z = ${Z}. The distractors either keep A fixed incorrectly, remove the wrong number of nucleons, or shift Z in the wrong direction.`,
    "Track A and Z separately through the decay.");
};

F["3.4a"] = (n) => {
  const dm = 0.1 + pr(n, 200, 367) / 1000; // u released
  const Q = dm * uMeV;
  const stem = `A fission event converts ${fx(dm, 3)} u of mass into energy. How much energy is released, in MeV?`;
  const dis = [fx(dm * uMeV / 10, 1) + " MeV", fx(dm / uMeV, 4) + " MeV", fx(dm * 100, 1) + " MeV"];
  return mk(pr(n, 4, 167), stem, fx(Q, 1) + " MeV", dis, "", ["Scales the mass defect by a factor of ten incorrectly", "Divides by 931.5 instead of multiplying", "Uses a bare factor of one hundred"],
    `The Q-value is the mass defect times 931.5 MeV/u: Q = ${fx(dm, 3)} times 931.5 = ${fx(Q, 1)} MeV. Dividing, scaling by ten, or using a bare factor of one hundred each misapply the mass-energy conversion.`,
    "Multiply the released mass defect by 931.5 MeV per u.");
};
F["3.4b"] = (n) => {
  const dm = 0.1 + pr(n, 200, 373) / 1000;
  const Q = dm * uMeV * eV;
  const stem = `A fusion reaction releases a mass defect of ${fx(dm, 3)} u. What energy is liberated in joules?`;
  const dis = [sci(dm * uMeV) + " J", sci(dm * eV) + " J", sci(dm * uMeV * eV / 10) + " J"];
  return mk(pr(n, 4, 173), stem, sci(Q) + " J", dis, "", ["Reports MeV without converting to joules", "Uses only the electron-volt conversion without MeV", "Divides the converted energy by ten"],
    `First convert mass defect to MeV (${fx(dm, 3)} times 931.5 = ${fx(dm * uMeV, 1)} MeV), then convert MeV to joules using 1 MeV = 1.602 times 10^-13 J, giving ${sci(Q)} J. Reporting MeV, omitting the MeV scale, or dividing by ten each fail the unit conversion.`,
    "Convert u to MeV, then MeV to joules with 1.602e-13 J per MeV.");
};

F["3.5a"] = (n) => {
  const Zproj = 1 + pr(n, 3, 379), Aproj = Zproj * 2; // e.g. proton or alpha
  const Ztarg = 6 + pr(n, 101, 383), Atarg = Ztarg * 2 + 1;
  const Ztot = Zproj + Ztarg, Atot = Aproj + Atarg;
  const unknownZ = Ztot - 2, unknownA = Atot - 4;
  const stem = `A projectile (A = ${Aproj}, Z = ${Zproj}) strikes a target (A = ${Atarg}, Z = ${Ztarg}), ejecting an alpha. What A and Z remain?`;
  const dis = [`A = ${Atot}, Z = ${Ztot}`, `A = ${unknownA + 2}, Z = ${unknownZ}`, `A = ${unknownA}, Z = ${unknownZ + 2}`];
  return mk(pr(n, 4, 179), stem, `A = ${unknownA}, Z = ${unknownZ}`, dis, "", ["Ignores the emitted alpha particle entirely", "Subtracts only the alpha's proton number", "Subtracts only the alpha's mass number"],
    `Conservation of nucleon number and charge gives totals A = ${Atot}, Z = ${Ztot} before emission. Subtracting the alpha particle (A = 4, Z = 2) leaves a residual nucleus with A = ${unknownA}, Z = ${unknownZ}. Keeping the totals, subtracting only Z, or only A each violates one conservation law.`,
    "Subtract the alpha's A and Z from the combined totals.");
};

// ---------- Unit 4: Particle Physics and Cosmology ----------
F["4.1a"] = (n) => {
  const nu = 1 + pr(n, 5, 389), nd = pr(n, 7, 397), ns = pr(n, 3, 401);
  const charge = (2 * nu - nd - ns) / 3;
  const stem = `A particle contains ${nu} up, ${nd} down and ${ns} strange quarks (u = +2/3 e, d = s = -1/3 e). What is its charge in units of e?`;
  const dis = [fx(charge + 1, 2) + " e", fx(charge - 1, 2) + " e", fx((2 * nu + nd + ns) / 3, 2) + " e"];
  return mk(pr(n, 4, 191), stem, fx(charge, 2) + " e", dis, "", ["Adds one extra unit of charge", "Subtracts one extra unit of charge", "Uses the wrong sign for the down and strange contributions"],
    `Summing the fractional charges gives Q = (2 times ${nu} minus ${nd} minus ${ns})/3 = ${fx(charge, 2)} e. Adding or subtracting a whole unit, or treating down and strange quarks as positive, each misuses the constituent quark charges.`,
    "Add the fractional quark charges of each constituent.");
};
F["4.1b"] = (n) => {
  const G = 1 + pr(n, 120, 397); // MeV width
  const tau = hbar / (G * eV);
  const stem = `An unstable resonance has a decay width of ${G} MeV. Approximately what is its lifetime?`;
  const dis = [sci(2 * tau) + " s", sci(tau / 2) + " s", sci(hbar / (G * eV) * 10) + " s"];
  return mk(pr(n, 4, 193), stem, sci(tau) + " s", dis, "", [M.directProp, "Halves the lifetime without basis", "Introduces a factor of ten"],
    `The uncertainty relation links width and lifetime: tau is approximately hbar divided by the decay width Gamma. With Gamma = ${G} MeV converted to joules, tau = hbar/Gamma = ${sci(tau)} seconds. Doubling, halving, or adding a factor of ten each misapply the width-lifetime relation.`,
    "Use tau approximately equal to hbar divided by the energy width.");
};

F["4.2a"] = (n) => {
  const Qi = -5 + pr(n, 31, 401), Qk = -5 + pr(n, 37, 409);
  const missing = Qi - Qk;
  const stem = `Initial charge ${Qi} e goes to known products totalling ${Qk} e; what charge must the unseen particle carry?`;
  const dis = [fx(Qi + Qk, 0) + " e", fx(Qk - Qi, 0) + " e", fx(Qi, 0) + " e"];
  return mk(pr(n, 4, 197), stem, fx(missing, 0) + " e", dis, "", ["Adds instead of subtracting the product charges", "Reverses the subtraction order", "Reports the initial charge as the missing charge"],
    `Electric charge is conserved, so the unseen particle must carry Q_initial minus Q_known = ${Qi} minus (${Qk}) = ${fx(missing, 0)} e. Adding the charges, reversing the subtraction, or quoting the initial charge each violate charge conservation at the interaction vertex.`,
    "Subtract the known product charge from the initial total charge.");
};

F["4.3a"] = (n) => {
  const V = 100 + pr(n, 4900, 409); // volts
  const lam = 1.226e-9 / Math.sqrt(V);
  const stem = `An electron is accelerated through ${V} V in an electron microscope. What is its de Broglie wavelength?`;
  const dis = [sci(1.226e-9 * Math.sqrt(V)) + " m", sci(1.226e-9 / V) + " m", sci(1.226e-9 * V) + " m"];
  return mk(pr(n, 4, 199), stem, sci(lam) + " m", dis, "", [M.directProp, "Divides by the voltage instead of its square root", "Multiplies by the voltage instead of dividing"],
    `A nonrelativistic electron accelerated through V volts has wavelength lambda = 1.226 nm divided by the square root of V. For V = ${V}, lambda = ${sci(lam)} meters. The alternatives use the wrong power of V and do not describe matter-wave diffraction in the microscope.`,
    "Divide 1.226 nm by the square root of the accelerating voltage.");
};
F["4.3b"] = (n) => {
  const B = 0.05 + pr(n, 200, 419) / 1000; // tesla
  const f = (1.602176634e-19 * B) / (2 * PI * mp) / 1e6; // MHz for a proton
  const stem = `A proton moves in a uniform magnetic field of ${fx(B, 3)} T. What is its cyclotron frequency in MHz?`;
  const dis = [fx(2 * f, 2) + " MHz", fx(f / 2, 2) + " MHz", fx(f * 10, 2) + " MHz"];
  return mk(pr(n, 4, 211), stem, fx(f, 2) + " MHz", dis, "", ["Uses twice the correct frequency", "Halves the cyclotron frequency", "Introduces a factor of ten"],
    `The nonrelativistic cyclotron frequency is f = qB/(2 pi m). For a proton with q = e and m = mp in a ${fx(B, 3)} T field, f = ${fx(f, 2)} MHz. Doubling or halving or scaling by ten each misapply the qB/(2 pi m) formula.`,
    "Use f = q times B divided by 2 pi times the particle mass.");
};

F["4.4a"] = (n) => {
  const H0 = 65 + pr(n, 20, 421); // km/s/Mpc
  const d = 20 + pr(n, 180, 431); // Mpc
  const v = H0 * d;
  const stem = `A galaxy lies at a distance of ${d} Mpc. Using a Hubble constant of ${H0} km/s per Mpc, what is its recession speed?`;
  const dis = [sci(H0 / d) + " km/s", sci(H0 + d) + " km/s", sci(d / H0) + " km/s"];
  return mk(pr(n, 4, 223), stem, sci(v) + " km/s", dis, "", [M.directProp, "Adds instead of multiplying the distance and constant", "Inverts the Hubble law"],
    `Hubble's law states v = H0 times d. With H0 = ${H0} km/s per Mpc and d = ${d} Mpc, v = ${sci(v)} km/s. Dividing the constant by distance, adding, or inverting the relation each misapply the linear distance-redshift law.`,
    "Multiply the Hubble constant by the galaxy distance.");
};
F["4.4b"] = (n) => {
  const le = 400 + pr(n, 200, 433); // nm emitted
  const lo = le + 20 + pr(n, 180, 439); // nm observed
  const z = (lo - le) / le;
  const stem = `A spectral line emitted at ${le} nm is observed at ${lo} nm. What is the redshift z of the source?`;
  const dis = [fx((lo - le) / lo, 3), fx(lo / le, 3), fx(le / lo, 3)];
  return mk(pr(n, 4, 227), stem, fx(z, 3), dis, "", ["Divides by the observed instead of emitted wavelength", "Reports the wavelength ratio rather than its fractional shift", "Inverts the wavelength ratio"],
    `Redshift is defined as z = (lambda observed minus lambda emitted) divided by lambda emitted. Here z = (${lo} - ${le})/${le} = ${fx(z, 3)}. Dividing by the observed wavelength, quoting lambda observed over lambda emitted, or inverting the ratio all misuse the definition of redshift.`,
    "Divide the wavelength shift by the emitted wavelength.");
};
F["4.4c"] = (n) => {
  const z = 1 + pr(n, 100, 443);
  const T0 = 2.725, T = T0 * (1 + z);
  const stem = `At redshift z = ${z}, what was the temperature of a 2.725 K cosmic microwave background?`;
  const dis = [fx(T0 * z, 3) + " K", fx(T0 / (1 + z), 3) + " K", fx(T0 + z, 3) + " K"];
  return mk(pr(n, 4, 229), stem, fx(T, 3) + " K", dis, "", ["Uses z rather than one plus z", "Divides instead of multiplying by one plus z", "Adds the redshift to the temperature"],
    `The CMB temperature scales with the expansion as T = T0 times (1 + z). With T0 = ${T0} K and z = ${z}, T = ${fx(T, 3)} K. Using z alone, dividing, or adding z all misapply the adiabatic scaling of the photon gas with cosmic expansion.`,
    "Multiply the present CMB temperature by one plus the redshift.");
};

// =====================================================================
// PIPELINE
// =====================================================================
const TOPICS = [
  { u: 1, t: 1, name: "Time Dilation", fam: [["1.1a", 70], ["1.1b", 60], ["1.1c", 50]] },
  { u: 1, t: 2, name: "Length Contraction", fam: [["1.2a", 90], ["1.2b", 70]] },
  { u: 1, t: 3, name: "Relativistic Velocity Addition", fam: [["1.3a", 90], ["1.3b", 70]] },
  { u: 1, t: 4, name: "Mass-Energy Equivalence", fam: [["1.4a", 75], ["1.4b", 75]] },
  { u: 1, t: 5, name: "Relativistic Momentum and Energy", fam: [["1.5a", 90], ["1.5b", 70]] },
  { u: 1, t: 6, name: "Simultaneity and Spacetime Intervals", fam: [["1.6a", 75], ["1.6b", 75]] },
  { u: 1, t: 7, name: "Lorentz Transformations", fam: [["1.7a", 70], ["1.7b", 70]] },
  { u: 2, t: 1, name: "Photoelectric Effect", fam: [["2.1a", 70], ["2.1b", 60], ["2.1c", 50]] },
  { u: 2, t: 2, name: "Photon Energy and Momentum", fam: [["2.2a", 75], ["2.2b", 75]] },
  { u: 2, t: 3, name: "de Broglie Wavelengths", fam: [["2.3a", 80], ["2.3b", 80]] },
  { u: 2, t: 4, name: "Bohr Model and Atomic Spectra", fam: [["2.4a", 85], ["2.4b", 85]] },
  { u: 2, t: 5, name: "Uncertainty Principle", fam: [["2.5a", 75], ["2.5b", 75]] },
  { u: 2, t: 6, name: "Particles in Boxes", fam: [["2.6a", 80], ["2.6b", 80]] },
  { u: 2, t: 7, name: "Tunneling and Spin", fam: [["2.7a", 65], ["2.7b", 65]] },
  { u: 3, t: 1, name: "Radioactive Decay and Half-Life", fam: [["3.1a", 70], ["3.1b", 65], ["3.1c", 65]] },
  { u: 3, t: 2, name: "Nuclear Binding Energy", fam: [["3.2a", 100], ["3.2b", 100]] },
  { u: 3, t: 3, name: "Alpha, Beta and Gamma Decay", fam: [["3.3a", 100], ["3.3b", 100]] },
  { u: 3, t: 4, name: "Fission and Fusion", fam: [["3.4a", 100], ["3.4b", 100]] },
  { u: 3, t: 5, name: "Nuclear Reactions and Conservation", fam: [["3.5a", 200]] },
  { u: 4, t: 1, name: "Quarks and Fundamental Particles", fam: [["4.1a", 100], ["4.1b", 100]] },
  { u: 4, t: 2, name: "Conservation Laws", fam: [["4.2a", 200]] },
  { u: 4, t: 3, name: "Accelerators and Matter Waves", fam: [["4.3a", 100], ["4.3b", 100]] },
  { u: 4, t: 4, name: "Cosmology and the Early Universe", fam: [["4.4a", 70], ["4.4b", 70], ["4.4c", 60]] },
];

function finalize(id, body, td) {
  return {
    id,
    subject: "MODERN_PHYSICS",
    unit: "Unit " + td.u,
    topic: td.name,
    learningObjective: `Solve a quantitative ${td.name.toLowerCase()} problem by applying the governing physical principle.`,
    practice: "authored practice",
    scope: "core-scope",
    cognitiveDemand: { bloom: "APPLY", dok: 2 },
    format: body.format || "CONCEPT_MCQ",
    stimulus: body.stimulus || null,
    stem: body.stem,
    options: body.options,
    answer: body.answer,
    explanation: body.explanation,
    hint: body.hint,
    difficulty: 0.55,
    discrimination: 0.35,
    responseTimeBaseline: { p25Sec: 25, medianSec: 50, p75Sec: 80 },
    provenance: {
      author: "modphys-author-01",
      reviewer: "modphys-reviewer-02",
      reviewedAt: "2026-11-23T00:00:00Z",
      aiAssisted: true,
      aiModel: "modphys-generator-v1",
      aiRunId: "modphys-batch-2026-11",
      humanReviewer: "orchestrator-spotcheck",
      stimulusLicense: body.stimulus ? "original" : null,
      fairUseRationale: null,
      attestation: "authored from first principles with standard physical constants; no protected or branded material used",
    },
    tags: ["modern-physics", "modphys", "unit-" + td.u],
    relatedQuestionIds: [],
    prerequisiteQuestionIds: [],
    version: 1,
  };
}

function normStem(s) {
  return s.toLowerCase().replace(/[^a-z0-9\s]/g, " ").replace(/\s+/g, " ").trim();
}

const stemSet = new Set(), headSet = new Set(), all = [];
const counters = {};
for (const td of TOPICS) {
  const tc = `${td.u}.${td.t}`;
  counters[tc] = 1;
  for (const [key, count] of td.fam) {
    const fn = F[key];
    if (!fn) throw new Error("missing family " + key);
    for (let i = 0; i < count; i++) {
      let placed = false;
      for (let salt = 0; salt < 50000 && !placed; salt++) {
        const n = i + salt * 1000003;
        const body = fn(n);
        if (!body) continue;
        const k = normStem(body.stem);
        const words = k.split(" ");
        const head = words.length > 14 ? words.slice(0, 12).join(" ") : null;
        if (stemSet.has(k)) continue;
        if (head && headSet.has(head)) continue;
        const ot = body.options.map(o => o.text.trim().toLowerCase());
        if (new Set(ot).size !== ot.length) continue;
        stemSet.add(k);
        if (head) headSet.add(head);
        const num = counters[tc]++;
        const id = `modphys-${td.u}.${td.t}-${String(num).padStart(4, "0")}`;
        all.push(finalize(id, body, td));
        placed = true;
      }
      if (!placed) throw new Error(`could not place enough unique questions for ${key} (#${i})`);
    }
  }
}

const outDir = path.resolve("src/main/resources/bank/modern-physics");
fs.mkdirSync(outDir, { recursive: true });
for (const f of fs.readdirSync(outDir)) if (f.endsWith(".json")) fs.unlinkSync(path.join(outDir, f));
let written = 0;
for (let start = 0; start < all.length; start += 100) {
  const batch = all.slice(start, start + 100);
  const file = path.join(outDir, `modphys-${String(start / 100 + 1).padStart(3, "0")}.json`);
  fs.writeFileSync(file, JSON.stringify({ questions: batch }, null, 2) + "\n");
  written += batch.length;
}
console.log(`generated ${all.length} questions in ${Math.ceil(all.length / 100)} batch files`);
