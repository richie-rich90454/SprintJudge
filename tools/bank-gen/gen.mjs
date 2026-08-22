// SprintJudge bundled library generator.
// Produces per-set JSON files + a master bundle consumed by first-boot seeding.
// Every expected output is COMPUTED here, so hidden test cases are correct by construction.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const SETS_DIR = path.join(ROOT, "seed", "sets");
const BUNDLE_OUT = path.join(ROOT, "seed", "master-bundle.json");
const RESOURCE_OUT = path.join(ROOT, "src", "main", "resources", "seed", "master-bundle.json");

// ---------- deterministic PRNG ----------
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const pick = (rng, arr) => arr[Math.floor(rng() * arr.length)];
const shuffle = (rng, arr) => {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
};
const int = (rng, lo, hi) => lo + Math.floor(rng() * (hi - lo + 1));

// ---------- scoring tiers (Q6) ----------
const TIER = {
  MCQ: { pts: 100, sec: 60 },
  BUG: { pts: 150, sec: 90 },
  OJ: { pts: 500, sec: 240, hardPts: 700, hardSec: 360 },
};

const NAMES = ["ana", "bo", "cy", "dee", "eli", "fay", "gus", "hana", "ivo", "jo"];
const WORDS = ["alpha", "beta", "gamma", "delta", "kappa", "zeta", "omega", "sigma"];

// =====================================================================
// JAVA
// =====================================================================
const javaOJ = [
  { topic: "arrays", title: "Array Sum",
    make(rng, d) {
      const n = d === 0 ? 4 : d === 1 ? 6 : 9;
      const a = Array.from({ length: n }, () => int(rng, -20, 60));
      const out = a.reduce((x, y) => x + y, 0);
      const inp = `${n}\n${a.join(" ")}`;
      return q(`Read n then n integers; print their sum.`,
        starterJava("int"), cases([[inp, String(out)], ...more(rng, () => {
          const k = int(rng, 3, 10); const b = Array.from({ length: k }, () => int(rng, -50, 90));
          return [`${k}\n${b.join(" ")}`, String(b.reduce((x, y) => x + y, 0))];
        })]));
    } },
  { topic: "arrays", title: "First Maximum Index",
    make(rng, d) {
      const n = d === 0 ? 5 : 7;
      const a = Array.from({ length: n }, () => int(rng, 1, 99));
      let mi = 0; a.forEach((v, i) => { if (v > a[mi]) mi = i; });
      return q(`Read n then n integers; print the index of the FIRST maximum.`,
        starterJava("int"), cases([[`${n}\n${a.join(" ")}`, String(mi)], ...more(rng, () => {
          const k = int(rng, 4, 12); const b = Array.from({ length: k }, () => int(rng, 0, 999));
          let m = 0; b.forEach((v, i) => { if (v > b[m]) m = i; });
          return [`${k}\n${b.join(" ")}`, String(m)];
        })]));
    } },
  { topic: "arrays", title: "Reversed Sequence",
    make(rng) {
      const n = int(rng, 4, 8);
      const a = Array.from({ length: n }, () => int(rng, 1, 100));
      return q(`Read n then n integers; print them reversed, space-separated.`,
        starterJava("int"), cases([[`${n}\n${a.join(" ")}`, a.slice().reverse().join(" ")],
        ...more(rng, () => { const k = int(rng, 3, 11); const b = Array.from({ length: k }, () => int(rng, 0, 50)); return [`${k}\n${b.join(" ")}`, b.slice().reverse().join(" ")]; })]));
    } },
  { topic: "search-sort", title: "Binary Search Index",
    make(rng, d) {
      const n = d === 2 ? 12 : 8;
      const a = Array.from({ length: n }, (_, i) => i * 3 + int(rng, 0, 2) + i); // strictly increasing-ish
      const uniq = [...new Set(a)].sort((x, y) => x - y);
      const hit = pick(rng, uniq); const miss = Math.min(...uniq) - 7;
      const t = d === 2 && rng() < 0.5 ? miss : hit;
      return q(`Read n, then n SORTED integers, then a target. Print its index or -1.`,
        starterJava("int"),
        cases([[`${uniq.length}\n${uniq.join(" ")}\n${hit}`, String(uniq.indexOf(hit))],
        [`${uniq.length}\n${uniq.join(" ")}\n${miss}`, "-1"],
        ...more(rng, () => { const s = [...new Set(Array.from({ length: 9 }, () => int(rng, 1, 200)))].sort((x, y) => x - y); const h = pick(rng, s); return [`${s.length}\n${s.join(" ")}\n${h}`, String(s.indexOf(h))]; })]));
    } },
  { topic: "search-sort", title: "Bubble Sort Swap Count",
    make(rng, d) {
      const n = d === 0 ? 5 : d === 1 ? 6 : 9;
      const a = Array.from({ length: n }, () => int(rng, 1, 50));
      let sw = 0; const b = [...a];
      for (let i = 0; i < b.length; i++) for (let j = 0; j < b.length - 1 - i; j++) if (b[j] > b[j + 1]) { [b[j], b[j + 1]] = [b[j + 1], b[j]]; sw++; }
      return q(`Read n then n integers; print how many swaps bubble sort performs.`,
        starterJava("int"), cases([[`${n}\n${a.join(" ")}`, String(sw)],
        ...more(rng, () => { const k = int(rng, 4, 8); const c = Array.from({ length: k }, () => int(rng, 1, 40)); let s2 = 0; const cc = [...c]; for (let i = 0; i < k; i++) for (let j = 0; j < k - 1 - i; j++) if (cc[j] > cc[j + 1]) { [cc[j], cc[j + 1]] = [cc[j + 1], cc[j]]; s2++; } return [`${k}\n${c.join(" ")}`, String(s2)]; })]));
    } },
  { topic: "arrays2d", title: "Main Diagonal Sum",
    make(rng, d) {
      const n = d === 0 ? 2 : d === 1 ? 3 : 4;
      const m = Array.from({ length: n }, () => Array.from({ length: n }, () => int(rng, 1, 20)));
      const diag = m.map((row, i) => row[i]).reduce((x, y) => x + y, 0);
      const inp = `${n}\n${m.map(r => r.join(" ")).join("\n")}`;
      return q(`Read n then an n×n matrix; print the sum of its main diagonal.`,
        starterJava("matrix"), cases([[inp, String(diag)], ...more(rng, () => { const k = int(rng, 2, 4); const mm = Array.from({ length: k }, () => Array.from({ length: k }, () => int(rng, 0, 30))); return [`${k}\n${mm.map(r => r.join(" ")).join("\n")}`, String(mm.map((r, i) => r[i]).reduce((x, y) => x + y, 0))]; })]));
    } },
  { topic: "strings", title: "Vowel Counter",
    make(rng) {
      const w = pick(rng, ["documentation", "interface", "polymorphism", "encapsulation"]) + pick(rng, ["", "x"]);
      const cnt = (s) => (s.match(/[aeiou]/gi) || []).length;
      return q(`Read one lowercase word; print the number of vowels.`,
        starterJava("string"), cases([[w, String(cnt(w))], ...more(rng, () => { const s = shuffle(rng, "aeioubcdfs".split("")).join("").slice(0, int(rng, 5, 9)); return [s, String(cnt(s))]; })]));
    } },
  { topic: "strings", title: "Longest Word Length",
    make(rng) {
      const ws = Array.from({ length: int(rng, 3, 5) }, () => pick(rng, WORDS) + (rng() < 0.4 ? pick(rng, ["s", "ly", "r"]) : ""));
      const len = Math.max(...ws.map(w => w.length));
      return q(`Read one line of words; print the length of the longest word.`,
        starterJava("string"), cases([[ws.join(" "), String(len)], ...more(rng, () => { const v = Array.from({ length: 4 }, () => pick(rng, WORDS)); return [v.join(" "), String(Math.max(...v.map(x => x.length)))]; })]));
    } },
  { topic: "objects", title: "Top Student",
    make(rng, d) {
      const n = d === 2 ? 5 : 3;
      const st = Array.from({ length: n }, () => ({ n: pick(rng, NAMES), s: int(rng, 40, 100) }));
      let best = st[0]; st.forEach(s => { if (s.s > best.s) best = s; });
      const inp = `${n}\n${st.map(s => `${s.n} ${s.s}`).join("\n")}`;
      return q(`Read n, then n lines "name score". Print the name of the highest scorer (first on ties).`,
        starterJava("student"), cases([[inp, best.n], ...more(rng, () => { const k = int(rng, 2, 5); const ss = Array.from({ length: k }, () => ({ n: pick(rng, NAMES), s: int(rng, 30, 99) })); let b2 = ss[0]; ss.forEach(x => { if (x.s > b2.s) b2 = x; }); return [`${k}\n${ss.map(x => `${x.n} ${x.s}`).join("\n")}`, b2.n]; })]));
    } },
  { topic: "objects", title: "Rectangle Area Totals",
    make(rng) {
      const n = int(rng, 2, 4);
      const rs = Array.from({ length: n }, () => ({ w: int(rng, 1, 12), h: int(rng, 1, 12) }));
      const total = rs.reduce((a, r) => a + r.w * r.h, 0);
      const inp = `${n}\n${rs.map(r => `${r.w} ${r.h}`).join("\n")}`;
      return q(`Read n, then n lines "width height". Print the TOTAL area of all rectangles.`,
        starterJava("rect"), cases([[inp, String(total)], ...more(rng, () => { const k = int(rng, 2, 5); const rr = Array.from({ length: k }, () => ({ w: int(rng, 2, 15), h: int(rng, 2, 15) })); return [`${k}\n${rr.map(x => `${x.w} ${x.h}`).join("\n")}`, String(rr.reduce((a, x) => a + x.w * x.h, 0))]; })]));
    } },
  { topic: "recursion", title: "Fibonacci Modulo",
    make(rng, d) {
      const n = d === 0 ? 10 : d === 1 ? 30 : 90;
      const MOD = 1000000007;
      let a = 0, b = 1;
      for (let i = 0; i < n; i++) { [a, b] = [b, (a + b) % MOD]; }
      return q(`Read n (up to ${n}); print F(n) modulo 1000000007 where F(0)=0, F(1)=1.`,
        starterJava("fib"), cases([[String(n), String(a)], ["1", "1"], ["2", "1"], ...more(rng, () => { const k = int(rng, 3, n); let x = 0, y = 1; for (let i = 0; i < k; i++) [x, y] = [y, (x + y) % MOD]; return [String(k), String(x)]; })]));
    } },
  { topic: "primitives", title: "Casting Behavior",
    make(rng) {
      const x = int(rng, 300, 900);
      const byteVal = ((x + 128) % 256) - 128; // (byte) cast semantics via wrap
      return q(`Read an int in [300,900]; print it cast to Java byte (low 8 bits signed).`,
        starterJava("int"), cases([[String(x), String(byteVal)], ...more(rng, () => { const y = int(rng, 300, 900); return [String(y), String(((y + 128) % 256) - 128)]; })]));
    } },
];

function starterJava(kind) {
  const heads = {
    int: `import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        // your code here\n    }\n}`,
    string: `import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        // your code here\n    }\n}`,
    matrix: `import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        // read n, then n rows of n integers\n    }\n}`,
    student: `import java.util.*;\n\nclass Student { String name; int score; }\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        // build Student objects and find the top scorer\n    }\n}`,
    rect: `import java.util.*;\n\nclass Rectangle { int width, height; int area() { return width*height; } }\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n    }\n}`,
    fib: `import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // fast iteration beats naive recursion here\n    }\n}`,
  };
  return heads[kind] ?? heads.int;
}

const javaBugs = [
  { t: "Off-by-one loop bound", lines: () => ({ codeLines: [
      "int total = 0;",
      "for (int i = 0; i <= items.length; i++) {",
      "    total += items[i];",
      "]" ], bugLine: 1 }) },
  { t: "Assignment instead of comparison", lines: () => ({ codeLines: [
      "if (found = true) {",
      "    System.out.println(\"yes\");",
      "]" ], bugLine: 0 }) },
  { t: "Wrong equality on Strings", lines: () => ({ codeLines: [
      "if (name == \"admin\") {",
      "    grant();",
      "]" ], bugLine: 0 }) },
  { t: "Integer division truncation", lines: () => ({ codeLines: [
      "double avg = sum / count;",
      "System.out.println(avg);" ], bugLine: 0 }) },
  { t: "Unreachable last element", lines: () => ({ codeLines: [
      "for (int i = 0; i < values.length - 1; i++) {",
      "    process(values[i]);",
      "]" ], bugLine: 0 }) },
  { t: "Mutating list while iterating", lines: () => ({ codeLines: [
      "for (String s : words) {",
      "    if (s.isEmpty()) words.remove(s);",
      "]" ], bugLine: 1 }) },
];

const javaMcq = [
  { topic: "objects", q: (r) => ({ stem: `Which access modifier allows a field to be accessed ONLY within its own class?`, opts: ["protected", "private", "public", "no modifier"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: `A constructor's name must match…`, opts: ["the class name and have no return type", "any method name with void return", "the package name", "the file name only"], ans: 0 }) },
  { topic: "inheritance", q: (r) => ({ stem: `Keyword used to call the parent-class constructor?`, opts: ["parent()", "base()", "super()", "this()"], ans: 2 }) },
  { topic: "inheritance", q: (r) => ({ stem: `In Java, a class can extend…`, opts: ["multiple classes", "one class only", "one class plus one interface as class", "none"], ans: 1 }) },
  { topic: "polymorphism", q: (r) => ({ stem: `Overloading is resolved at…`, opts: ["compile time by signature", "runtime by object type", "link time", "garbage collection"], ans: 0 }) },
  { topic: "polymorphism", q: (r) => ({ stem: `Dynamic dispatch chooses the method by…`, opts: ["reference type at compile time", "actual object type at runtime", "alphabetical order", "parameter count only"], ans: 1 }) },
  { topic: "arrays", q: (r) => { const n = int(r, 3, 9); return { stem: `int[] a = new int[${n}]; What is a[a.length - 1] initially?`, opts: ["0", "null", "undefined", "throws exception"], ans: 0 }; } },
  { topic: "arrays", q: (r) => ({ stem: `Valid way to get an array's element count?`, opts: ["a.length()", "a.size()", "a.length", "len(a)"], ans: 2 }) },
  { topic: "lists", q: (r) => ({ stem: `ArrayList vs array — which is TRUE?`, opts: ["ArrayList is fixed-size", "arrays auto-resize", "ArrayList resizes dynamically", "both are identical"], ans: 2 }) },
  { topic: "lists", q: (r) => ({ stem: `Correct generic declaration?`, opts: ['List<int> l', 'ArrayList<int> l', 'List<Integer> l', 'Integer[]<int>'], ans: 2 }) },
  { topic: "strings", q: (r) => ({ stem: `"abc".equals("ABC") returns…`, opts: ["true", "false", "compile error", "depends on locale"], ans: 1 }) },
  { topic: "strings", q: (r) => ({ stem: `Strings in Java are…`, opts: ["mutable", "immutable", "primitive", "always pooled manually"], ans: 1 }) },
  { topic: "recursion", q: (r) => ({ stem: `Every correct recursive method needs…`, opts: ["a loop", "a base case", "static keyword", "try/catch"], ans: 1 }) },
  { topic: "search-sort", q: (r) => ({ stem: `Binary search requires the input to be…`, opts: ["unique values only", "sorted", "numeric only", "small"], ans: 1 }) },
  { topic: "search-sort", q: (r) => ({ stem: `Selection sort on n elements performs roughly…`, opts: ["n² comparisons", "n comparisons", "log n comparisons", "n! comparisons"], ans: 0 }) },
  { topic: "arrays2d", q: (r) => ({ stem: `grid[r][c] accesses…`, opts: ["column r row c", "row r column c", "char at r,c of string", "invalid"], ans: 1 }) },
  { topic: "primitives", q: (r) => ({ stem: `(int) 7.9 evaluates to…`, opts: ["8", "7", "7.0", "error"], ans: 1 }) },
  { topic: "primitives", q: (r) => ({ stem: `Widening conversion happens…`, opts: ["explicitly with casts only", "automatically smaller→larger", "larger→smaller automatically", "never"], ans: 1 }) },
];

// =====================================================================
// PYTHON
// =====================================================================
const pyStarter = `# read input with input() / sys.stdin\n`;
const pyOJ = [
  { topic: "arrays", title: "List Statistics", make(rng, d) {
      const n = d === 0 ? 5 : 7; const a = Array.from({length:n},()=>int(rng,-15,80));
      return q(`Read n then n integers on one line; print "min max sum" space-separated.`,
        pyStarter, cases([[`${n}\n${a.join(' ')}`, `${Math.min(...a)} ${Math.max(...a)} ${a.reduce((x,y)=>x+y,0)}`], ...more(rng, ()=>{const k=int(rng,3,9); const b=Array.from({length:k},()=>int(rng,-30,120)); return [`${k}\n${b.join(' ')}`, `${Math.min(...b)} ${Math.max(...b)} ${b.reduce((x,y)=>x+y,0)}`];})])); } },
  { topic: "strings", title: "Word Frequency Top", make(rng) {
      const ws = Array.from({length:int(rng,5,8)},()=>pick(rng,WORDS));
      const counts = {}; ws.forEach(w=>counts[w]=(counts[w]||0)+1);
      const best = Object.entries(counts).sort((x,y)=> y[1]-x[1] || x[0].localeCompare(y[0]))[0][0];
      return q(`Read one line; print the most frequent word (ties: alphabetical first).`,
        pyStarter, cases([[ws.join(' '), best], ...more(rng, ()=>{const v=shuffle(rng,WORDS).slice(0,4).concat([pick(rng,WORDS)]); const c={}; v.forEach(w=>c[w]=(c[w]||0)+1); const b=Object.entries(c).sort((x,y)=>y[1]-x[1]||x[0].localeCompare(y[0]))[0][0]; return [v.join(' '), b];})])); } },
  { topic: "lists", title: "Filtered Squares Sum", make(rng, d) {
      const n = d===0?5:8; const a=Array.from({length:n},()=>int(rng,-12,25));
      const s=a.filter(v=>v>0).map(v=>v*v).reduce((x,y)=>x+y,0);
      return q(`Read n then n integers; print the sum of squares of the POSITIVE numbers.`,
        pyStarter, cases([[`${n}\n${a.join(' ')}`, String(s)], ...more(rng, ()=>{const k=int(rng,4,9); const b=Array.from({length:k},()=>int(rng,-20,30)); return [`${k}\n${b.join(' ')}`, String(b.filter(v=>v>0).map(v=>v*v).reduce((x,y)=>x+y,0))];})])); } },
  { topic: "objects", title: "Class Instance Count", make(rng) {
      // Simulated: commands create/count instances -> final active count
      const cmds=[]; let live=0; const log=[];
      for(let i=0;i<int(rng,5,8);i++){ const op=pick(rng,['new','del']); if(op==='new'){live++;cmds.push('new');log.push(live);}else if(live>0){live--;cmds.push('del');log.push(live);}else{cmds.push('del');log.push(live);} }
      return q(`Process commands "new"/"del" (one per line, "del" never exceeds created). After each line print the LIVE instance count.`,
        pyStarter, cases([[cmds.join('\n'), log.join('\n')], ...more(rng, ()=>{const c2=[];const l2=[];let lv=0;for(let i=0;i<6;i++){const o=pick(rng,['new','new','del']);if(o==='new'){lv++;}else if(lv>0){lv--;}c2.push(o);l2.push(lv);}return [c2.join('\n'), l2.join('\n')];})])); } },
  { topic: "arrays2d", title: "Row Sums", make(rng, d) {
      const r=d===0?2:d===1?3:4, c=d===0?3:r; 
      const m=Array.from({length:r},()=>Array.from({length:c},()=>int(rng,0,25)));
      const sums=m.map(row=>row.reduce((x,y)=>x+y,0));
      return q(`Read R C then the matrix rows; print each row sum on its own line.`,
        pyStarter, cases([[`${r} ${c}\n${m.map(x=>x.join(' ')).join('\n')}`, sums.join('\n')], ...more(rng, ()=>{const rr=int(rng,2,4),cc=int(rng,2,4);const mm=Array.from({length:rr},()=>Array.from({length:cc},()=>int(rng,1,30)));return [`${rr} ${cc}\n${mm.map(x=>x.join(' ')).join('\n')}`, mm.map(row=>row.reduce((x,y)=>x+y,0)).join('\n')];})])); } },
  { topic: "recursion", title: "Digit Sum", make(rng) {
      const n=int(rng,10,999999);
      const ds=String(n).split('').reduce((x,y)=>x+ +y,0);
      return q(`Read a non-negative integer; print the sum of its digits.`,
        pyStarter, cases([[String(n),String(ds)], ...more(rng, ()=>{const k=int(rng,10,999999);return [String(k),String(String(k).split('').reduce((x,y)=>x+ +y,0))];})])); } },
  { topic: "search-sort", title: "Sort Words Alphabetically", make(rng) {
      const ws=shuffle(rng,WORDS).slice(0,int(rng,4,6));
      const sorted=[...ws].sort();
      return q(`Read one line of words; print them alphabetically sorted, space-separated.`,
        pyStarter, cases([[ws.join(' '), sorted.join(' ')], ...more(rng, ()=>{const v=shuffle(rng,WORDS).slice(0,5);return [v.join(' '), [...v].sort().join(' ')];})])); } },
  { topic: "primitives", title: "Type of Result", make(rng) {
      const a=int(rng,2,9), b=pick(rng,[2,4,5]);
      return q(`Read two integers A and B; print A**B (power).`,
        pyStarter, cases([[`${a} ${b}`, String(Math.pow(a,b))], ...more(rng, ()=>{const x=int(rng,2,7),y=int(rng,2,5);return [`${x} ${y}`,String(Math.pow(x,y))];})])); } },
];
const pyBugs = [ /*normalized*/
  { t:"Mutable default argument", lines:(r)=>[`def add_item(item, items=[]):`,`    items.append(item)`,`    return items`], bugLine: 0 },
  { t:"Assignment in comparison", lines:(r)=>[`if status = "ready":`,`    launch()`], bugLine: 0 },
  { t:"Indentation changes scope", lines:(r)=>[`total = 0`,`for x in nums:`,`    total += x`,`print(total)`], bugLine: 3 },
  { t:"Range excludes end", lines:(r)=>[`for i in range(1, n):`,`    print(i)`], bugLine: 0 },
  { t:"is vs ==", lines:(r)=>[`if name is "admin":`,`    unlock()`], bugLine: 0 },
  { t:"Modifying while iterating", lines:(r)=>[`for w in words:`,`    if not w: words.remove(w)`], bugLine: 1 },
];
const pyMcq = [
  { topic:"primitives", q:(r)=>({stem:`type(3 / 2) in Python 3 is…`,opts:["int","float","Decimal","error"],ans:1})},
  { topic:"lists", q:(r)=>({stem:`nums = [1,2,3]; nums.append([4,5]). len(nums)?`,opts:["5","3","4","error"],ans:2})},
  { topic:"strings", q:(r)=>({stem:`"py" * 3 evaluates to…`,opts:["type error","'pyypy'","'py3'","'pypypypypy'"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`self in a Python method refers to…`,opts:["the class","the module","the instance","global scope"],ans:2})},
  { topic:"objects", q:(r)=>({stem:`__init__ is called when…`,opts:["class is defined","instance is created","module imported","object deleted"],ans:1})},
  { topic:"recursion", q:(r)=>({stem:`Missing base case causes…`,opts:["zero iterations","RecursionError","silent skip","auto stop at 100"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`Slicing lst[1:4] includes indices…`,opts:["1,2,3,4","1,2,3","2,3,4","1 and 4"],ans:1})},
  { topic:"search-sort", q:(r)=>({stem:`sorted() vs .sort(): which mutates?`,opts:["sorted()",".sort()","both","neither"],ans:1})},
  { topic:"arrays2d", q:(r)=>({stem:`m[row][col] — outer index selects…`,opts:["column","row","cell value","diagonal"],ans:1})},
  { topic:"lists", q:(r)=>({stem:`Deep-copy need arises because assignment…`,opts:["copies fully","shares the reference","converts type","raises"],ans:1})},
];

// =====================================================================
// C++
// =====================================================================
const cppStarter = `#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    // your code here\n}\n`;
const cppOJ = [
  { topic:"search-sort", title:"Ascending Sort Output", make(rng,d){
      const n=d===0?5:d===1?8:12; const a=Array.from({length:n},()=>int(rng,-40,400));
      const s=[...a].sort((x,y)=>x-y);
      return q(`Read n then n integers; print them ascending, space-separated.`,
        cppStarter, cases([[`${n}\n${a.join(' ')}`, s.join(' ')], ...more(rng, ()=>{const k=int(rng,4,14);const b=Array.from({length:k},()=>int(rng,0,500));return [`${k}\n${b.join(' ')}`, [...b].sort((x,y)=>x-y).join(' ')];})])); } },
  { topic:"objects", title:"Best Seller Struct", make(rng,d){
      const n=d===0?3:5;
      const rows=Array.from({length:n},()=>({name:pick(rng,NAMES),units:int(rng,5,120)}));
      let best=rows[0]; rows.forEach(x=>{if(x.units>best.units)best=x;});
      return q(`Read n, then "name units" lines. Print the name with the MOST units.`,
        `#include <bits/stdc++.h>\nusing namespace std;\nstruct Seller { string name; int units; };\n\nint main() {}\n`,
        cases([[`${n}\n${rows.map(x=>`${x.name} ${x.units}`).join('\n')}`, best.name], ...more(rng, ()=>{const k=int(rng,2,6);const rr=Array.from({length:k},()=>({name:pick(rng,NAMES),units:int(rng,3,99)}));let b=rr[0];rr.forEach(x=>{if(x.units>b.units)b=x;});return [`${k}\n${rr.map(x=>`${x.name} ${x.units}`).join('\n')}`, b.name];})])); } },
  { topic:"strings", title:"Longest Word Length", make(rng){
      const ws=Array.from({length:int(rng,3,5)},()=>pick(rng,WORDS)+(rng()<0.3?'ly':''));
      return q(`Read one line of words; print the longest word's LENGTH.`,
        cppStarter, cases([[ws.join(' '), String(Math.max(...ws.map(w=>w.length)))], ...more(rng, ()=>{const v=Array.from({length:4},()=>pick(rng,WORDS));return [v.join(' '), String(Math.max(...v.map(x=>x.length)))];})])); } },
  { topic:"primitives", title:"Prime Checker", make(rng,d){
      const p=pick(rng,d===2?[97,89,79]:[13,17,29]); const comp=p+pick(rng,[1,3,4]);
      return q(`Read an integer >1; print "prime" or "not prime".`,
        cppStarter, cases([[String(p),'prime'],[String(comp),'not prime'], ...more(rng, ()=>{const k=int(rng,2,400);let isp=k>1;for(let dd=2;dd*dd<=k;dd++){if(k%dd===0){isp=false;break;}}return [String(k),isp?'prime':'not prime'];})])); } },
  { topic:"arrays", title:"Unique Values In Sorted Input", make(rng){
      const base=Array.from({length:int(rng,6,9)},()=>int(rng,1,60)); const s=[...new Set(base)].sort((x,y)=>x-y);
      const withDup=[]; s.forEach((v,i)=>{withDup.push(v); if(i%2===0) withDup.push(v);});
      const shuffled=shuffle(rng,withDup); const uniq=[...new Set(shuffled)].sort((x,y)=>x-y);
      return q(`Read n then n integers; print the DISTINCT values ascending, space-separated.`,
        cppStarter, cases([[`${shuffled.length}\n${shuffled.join(' ')}`, uniq.join(' ')], ...more(rng, ()=>{const u=[...new Set(Array.from({length:8},()=>int(rng,1,90)))].sort((x,y)=>x-y);const dup=[];u.forEach((v,i)=>{dup.push(v,v);});return [`${dup.length}\n${dup.join(' ')}`, u.join(' ')];})])); } },
  { topic:"arrays2d", title:"Row Maximums", make(rng,d){
      const r=d===0?2:3, c=r+1;
      const m=Array.from({length:r},()=>Array.from({length:c},()=>int(rng,-20,70)));
      const mx=m.map(row=>Math.max(...row));
      return q(`Read R C then the matrix; print each ROW maximum, one per line.`,
        cppStarter, cases([[`${r} ${c}\n${m.map(x=>x.join(' ')).join('\n')}`, mx.join('\n')], ...more(rng, ()=>{const rr=int(rng,2,4),cc=3;const mm=Array.from({length:rr},()=>Array.from({length:cc},()=>int(rng,0,99)));return [`${rr} ${cc}\n${mm.map(x=>x.join(' ')).join('\n')}`, mm.map(row=>Math.max(...row)).join('\n')];})])); } },
];
const cppBugs = [
  { t:"Off-by-one vector loop", lines:(r)=>[`for (size_t i = 0; i <= v.size(); ++i) {`,`    cout << v[i];`,`}`], bugLine: 0 },
  { t:"Post-increment misuse", lines:(r)=>[`int half = n / 2++;`], bugLine: 0 },
  { t:"Dangling reference return", lines:(r)=>[`int& bad() { int x = 5; return x; }`], bugLine: 0 },
  { t:"= in condition", lines:(r)=>[`if (ok = false) retry();`], bugLine: 0 },
  { t:"Signed/unsigned compare", lines:(r)=>[`for (int i = 0; i < v.size(); ++i) cout << v[i];`], bugLine: 0 },
  { t:"Missing break in switch", lines:(r)=>[`case 1: start();`,`case 2: stop();`], bugLine: 0 },
];
const cppMcq = [
  { topic:"objects", q:(r)=>({stem:`RAII ties resource lifetime to…`,opts:["manual delete calls","object scope","garbage collector","static storage"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Rule of three applies when a class has…`,opts:["only static members","user-defined destructor/copy ops","templates","no constructor"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`sizeof(bool) is guaranteed at least…`,opts:["1 nibble","1 byte","4 bytes","implementation zero"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`std::vector growth is…`,opts:["linear each push","amortized constant push","fixed capacity always","O(n log n)"],ans:1})},
  { topic:"strings", q:(r)=>({stem:`std::string copies are…`,opts:["shallow by default","deep by value","references","views"],ans:1})},
  { topic:"search-sort", q:(r)=>({stem:`std::sort worst-case (introsort) is…`,opts:["O(n)","O(n log n)","O(n²)","O(log n)"],ans:1})},
  { topic:"arrays2d", q:(r)=>({stem:`vector<vector<int>> grid(r, vector<int>(c)) creates…`,opts:["flat array","r rows of c zeros","jagged empty","c rows of r"],ans:1})},
  { topic:"inheritance", q:(r)=>({stem:`Virtual destructor matters when deleting via…`,opts:["stack objects","base pointer","const ref","template"],ans:1})},
  { topic:"polymorphism", q:(r)=>({stem:`override keyword…`,opts:["creates virtual","checks signature correctness","disables overload","is Java-only"],ans:1})},
  { topic:"recursion", q:(r)=>({stem:`Deep recursion risks…`,opts:["stack overflow","heap leak","UB always","nothing"],ans:0})},
];

// =====================================================================
// C
// =====================================================================
const cStarter = `#include <stdio.h>\n\nint main(void) {\n    // your code here\n    return 0;\n}\n`;
const cOJ = [
  { topic:"arrays", title:"Array Sum (scanf)", make(rng,d){
      const n=d===0?4:6; const a=Array.from({length:n},()=>int(rng,1,90));
      return q(`Read n then n integers; print their sum.`,
        cStarter, cases([[`${n}\n${a.join(' ')}`, String(a.reduce((x,y)=>x+y,0))], ...more(rng, ()=>{const k=int(rng,3,9);const b=Array.from({length:k},()=>int(rng,0,120));return [`${k}\n${b.join(' ')}`,String(b.reduce((x,y)=>x+y,0))];})])); } },
  { topic:"primitives", title:"Swap Two Integers", make(rng){
      const a=int(rng,1,500), b=int(rng,1,500);
      return q(`Read two integers A B; print "B A" (swapped), space-separated.`,
        cStarter, cases([[`${a} ${b}`, `${b} ${a}`], ...more(rng, ()=>{const x=int(rng,1,999),y=int(rng,1,999);return [`${x} ${y}`,`${y} ${x}`];})])); } },
  { topic:"strings", title:"String Length Compare", make(rng){
      const w1=pick(rng,WORDS), w2=pick(rng,WORDS);
      const cmp=w1.length>w2.length?'LONGER':w1.length<w2.length?'SHORTER':'EQUAL';
      return q(`Read two whitespace-separated words; print LONGER, SHORTER, or EQUAL (word1 vs word2 lengths).`,
        cStarter, cases([[`${w1} ${w2}`,cmp], ...more(rng, ()=>{const x=pick(rng,WORDS),y=pick(rng,WORDS);const c=x.length>y.length?'LONGER':x.length<y.length?'SHORTER':'EQUAL';return [`${x} ${y}`,c];})])); } },
  { topic:"recursion", title:"Power Function", make(rng,d){
      const base=int(rng,2,5), exp=d===2?9:int(rng,3,7);
      return q(`Read BASE EXPONENT; print base^exponent (integer).`,
        cStarter, cases([[`${base} ${exp}`,String(Math.pow(base,exp))], ...more(rng, ()=>{const x=int(rng,2,6),y=int(rng,2,8);return [`${x} ${y}`,String(Math.pow(x,y))];})])); } },
  { topic:"search-sort", title:"Bubble Sort Ascending", make(rng){
      const n=int(rng,4,7); const a=Array.from({length:n},()=>int(rng,1,80));
      return q(`Read n then n integers; print them ascending, space-separated.`,
        cStarter, cases([[`${n}\n${a.join(' ')}`, [...a].sort((x,y)=>x-y).join(' ')], ...more(rng, ()=>{const k=int(rng,4,9);const b=Array.from({length:k},()=>int(rng,0,150));return [`${k}\n${b.join(' ')}`,[...b].sort((x,y)=>x-y).join(' ')];})])); } },
  { topic:"primitives", title:"Digit Count", make(rng){
      const n=int(rng,1,99999);
      return q(`Read a positive integer; print how many decimal digits it has.`,
        cStarter, cases([[String(n),String(String(n).length)], ...more(rng, ()=>{const k=int(rng,1,999999);return [String(k),String(String(k).length)];})])); } },
  { topic:"arrays", title:"Minimum and Its Index", make(rng){
      const n=int(rng,4,7); const a=Array.from({length:n},()=>int(rng,-30,90));
      let mi=0; a.forEach((v,i)=>{if(v<a[mi])mi=i;});
      return q(`Read n then n integers; print "min index" (first minimum), space-separated.`,
        cStarter, cases([[`${n}\n${a.join(' ')}`,`${a[mi]} ${mi}`], ...more(rng, ()=>{const k=int(rng,3,8);const b=Array.from({length:k},()=>int(rng,-50,60));let m=0;b.forEach((v,i)=>{if(v<b[m])m=i;});return [`${k}\n${b.join(' ')}`,`${b[m]} ${m}`];})])); } },
];
const cBugs = [
  { t:"Buffer overflow scanf", lines:(r)=>[`char buf[8];`,`scanf("%s", buf);`], bugLine: 1 },
  { t:"Missing & in scanf", lines:(r)=>[`int x;`,`scanf("%d", x);`], bugLine: 1 },
  { t:"== vs =", lines:(r)=>[`if (flag == 0)`, `    reset();`], bugLine: 0 },
  { t:"Off-by-one <= size", lines:(r)=>[`for (int i = 0; i <= n; i++)`,`    sum += a[i];`], bugLine: 0 },
  { t:"Uninitialized local", lines:(r)=>[`int total;`,`printf("%d\\n", total);`], bugLine: 0 },
  { t:"Return local address", lines:(r)=>[`char* name() { char b[16]; return b; }`], bugLine: 0 },
];
const cMcq = [
  { topic:"primitives", q:(r)=>({stem:`printf("%d", 7 / 2) prints…`,opts:["3.5","3","4","undefined"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`int a[5]; valid indices are…`,opts:["0..5","1..5","0..4","1..4"],ans:2})},
  { topic:"arrays", q:(r)=>({stem:`Arrays in C are passed to functions as…`,opts:["copies","pointers to first element","references only","globals"],ans:1})},
  { topic:"strings", q:(r)=>({stem:`A C string ends with…`,opts:["newline","space","'\\0'","EOF"],ans:2})},
  { topic:"strings", q:(r)=>({stem:`strlen("quiz") equals…`,opts:["5","4","8","3"],ans:1})},
  { topic:"recursion", q:(r)=>({stem:`Recursion in C uses…`,opts:["heap frames","call stack frames","static storage","registers only"],ans:1})},
  { topic:"search-sort", q:(r)=>({stem:`Linear search complexity on n items is…`,opts:["O(1)","O(log n)","O(n)","O(n log n)"],ans:2})},
  { topic:"search-sort", q:(r)=>({stem:`Best case for bubble sort (optimized with early exit)?`,opts:["O(n²)","O(n)","O(1)","O(log n)"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`sizeof(char) is…`,opts:["implementation-defined ≥1","always 1 byte","2 bytes","4 bytes"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`a[i] is equivalent to…`,opts:["*(a+i)","&a[i]","a+i","(*a)+i"],ans:0})},
];

// ---------- helpers ----------
// Archetypes build ONE flat array of [input, expected] pairs; accept it as-is.
function cases(pairs) {
  if (!Array.isArray(pairs)) throw new TypeError("cases(): expected an array of [input, expected] pairs");
  return pairs;
}
function more(rng, f) {
  return [0,1,2,3,4,5,6,7,8,9].map(() => f());
}
function q(statement, starter, casePairs) {
  const seen = new Set();
  const testCases = [];
  for (const [input, expectedOutput] of casePairs) {
    if (seen.has(input)) continue;
    seen.add(input);
    testCases.push({ input: input + "\n", expectedOutput, isHidden: testCases.length >= 2 });
    if (testCases.length === 6) break;
  }
  return { __oj: true, statement, starter, testCases };
}

function buildQuestion(lang, spec, quizId, idx, tier) {
  if (spec.__oj) {
    return {
      id: `${quizId}-q${idx}`, type: "OJ_FULL",
      title: spec.title, description: spec.statement + "\n\n### Starter\n```\n" + spec.starter + "\n```",
      timeLimitSec: tier.sec, pointsBase: tier.pts,
      config: { testCases: spec.testCases, memoryLimitMb: 256, starter: spec.starter },
      languagesAllowed: LANG_IDS[lang],
    };
  }
  if (spec.kind === "bug") {
    return {
      id: `${quizId}-q${idx}`, type: "CLICK_BUG",
      title: "Spot the Bug: " + spec.title, description: "Click the line that contains the bug.",
      timeLimitSec: TIER.BUG.sec, pointsBase: TIER.BUG.pts,
      config: { codeLines: spec.codeLines, bugLine: spec.bugLine },
      languagesAllowed: null,
    };
  }
  return {
    id: `${quizId}-q${idx}`, type: "MCQ",
    title: spec.stem.slice(0, 60) + (spec.stem.length > 60 ? "…" : ""),
    description: spec.stem,
    timeLimitSec: TIER.MCQ.sec, pointsBase: TIER.MCQ.pts,
    config: { options: spec.opts, correctIndex: spec.ans },
    languagesAllowed: null,
  };
}

const LANG_IDS = {
  java: ["java"], python: ["python"], cpp: ["cpp"], c: ["c"],
};

// ---------- assembly ----------
function assembleLanguage(lang, cfg) {
  const ojPool = lang === "java" ? javaOJ : lang === "python" ? pyOJ : lang === "cpp" ? cppOJ : cOJ;
  const bugPool = lang === "java" ? javaBugs : lang === "python" ? pyBugs : lang === "cpp" ? cppBugs : cBugs;
  const mcqPool = lang === "java" ? javaMcq : lang === "python" ? pyMcq : lang === "cpp" ? cppMcq : cMcq;

  const quizzes = [];
  let n = 0;
  for (const band of cfg.bands) {           // [{label:'Foundations',count:18}]
    for (let i = 0; i < band.count; i++) {
      const rng = mulberry32(cfg.seedBase + n * 7919 + hashStr(band.label));
      const diff = band.diff;                // 0 easy, 1 medium, 2 hard
      const topic = cfg.topics[n % cfg.topics.length];
      const quizId = `${lang}-${band.label.toLowerCase().slice(0, 1)}${String(n + 1).padStart(2, "0")}`;

      const ojSpecs = shuffle(rng, ojPool.filter(o => o.topic === topic))
        .concat(shuffle(rng, ojPool)).slice(0, 3);
      const bugIdx = shuffle(rng, bugPool.keys()).slice(0, 3);
      const mcqs = [];
      const topicMcq = shuffle(rng, mcqPool.filter(m => m.topic === topic));
      const restMcq = shuffle(rng, mcqPool.filter(m => m.topic !== topic));
      const usedStems = new Set();
      let guard = 0;
      while (mcqs.length < 4 && guard++ < 60) {
        // Prefer on-topic items early; dedupe is best-effort, never a deadlock.
        const prefer = mcqs.length < 2;
        let candidates = prefer ? mcqPool.filter(m => m.topic === topic) : [];
        if (!candidates.length) candidates = mcqPool;
        const built = pick(rng, candidates).q(rng);
        if (usedStems.has(built.stem)) continue;
        usedStems.add(built.stem);
        mcqs.push(built);
      }
      while (mcqs.length < 4) mcqs.push(pick(rng, mcqPool).q(rng));

      const questions = [];
      let qi = 1;
      const ojTier = diff === 2
        ? { pts: TIER.OJ.hardPts, sec: TIER.OJ.hardSec }
        : { pts: TIER.OJ.pts, sec: TIER.OJ.sec };
      for (const spec of ojSpecs) {
        const built = spec.make(rng, Math.min(diff, specHasDiff(spec) ? diff : 0));
        const qObj = buildQuestion(lang, { ...built, title: built.title ?? spec.title }, quizId, qi++, ojTier);
        qObj.title = `${spec.title}`;
        questions.push(qObj);
      }
      for (const bi of bugIdx) {
        const b = bugPool[bi];
        const raw = b.lines(rng);
        const codeLines = Array.isArray(raw) ? raw : raw.codeLines;
        const bugLine = Number((Array.isArray(raw) ? b.bugLine : raw.bugLine ?? b.bugLine) ?? 0);
        questions.push(buildQuestion(lang, {
          kind: "bug", title: b.t,
          codeLines, bugLine,
        }, quizId, qi++, null));
      }
      for (const mc of mcqs) questions.push(buildQuestion(lang, mc, quizId, qi++, null));

      quizzes.push({
        id: quizId,
        title: `${cap(lang)} · ${cap(topic)} — ${band.label} ${String(i + 1).padStart(2, "0")}`,
        description: `Ten-question ${cap(lang)} practice set focused on ${topic}. Graded ${band.label}.`,
        questions,
      });
      n++;
    }
  }
  return quizzes;
}
function specHasDiff(spec){ return spec.make.length >= 2; }
function cap(s){ return s.charAt(0).toUpperCase()+s.slice(1); }
function hashStr(s){ let h=0; for(const ch of s) h=(h*31+ch.charCodeAt(0))|0; return Math.abs(h); }

const PLAN = [
  { lang:"java",   bands:[{label:"Foundations",diff:0,count:18},{label:"Intermediate",diff:1,count:20},{label:"Advanced",diff:2,count:12}],
    topics:["objects","arrays","arrays","strings","lists","search-sort","recursion","arrays2d","polymorphism","inheritance","primitives","objects"] , seedBase:101},
  { lang:"python", bands:[{label:"Foundations",diff:0,count:7},{label:"Intermediate",diff:1,count:8},{label:"Advanced",diff:2,count:5}],
    topics:["lists","strings","objects","arrays","recursion","search-sort","arrays2d","primitives","lists","strings"], seedBase:202 },
  { lang:"cpp",    bands:[{label:"Foundations",diff:0,count:7},{label:"Intermediate",diff:1,count:8},{label:"Advanced",diff:2,count:5}],
    topics:["arrays","objects","strings","search-sort","arrays2d","primitives","inheritance","polymorphism","recursion","arrays"], seedBase:303 },
  { lang:"c",      bands:[{label:"Foundations",diff:0,count:4},{label:"Intermediate",diff:1,count:4},{label:"Advanced",diff:2,count:2}],
    topics:["arrays","primitives","strings","recursion","search-sort","arrays","primitives","strings","arrays","recursion"], seedBase:404 },
];

fs.rmSync(SETS_DIR, { recursive: true, force: true });
const bundle = { version: "1.0", exportedAt: Date.now(), quizzes: [], adminSettings: {} };
let totalQ = 0;
for (const plan of PLAN) {
  const dir = path.join(SETS_DIR, plan.lang);
  fs.mkdirSync(dir, { recursive: true });
  const quizzes = assembleLanguage(plan.lang, plan);
  for (const qz of quizzes) {
    totalQ += qz.questions.length;
    const doc = { version: "1.0", exportedAt: Date.now(), adminSettings: {}, quizzes: [qz] };
    fs.writeFileSync(path.join(dir, qz.id + ".json"), JSON.stringify(doc, null, 1));
    bundle.quizzes.push(qz);
  }
}
fs.mkdirSync(path.dirname(BUNDLE_OUT), { recursive: true });
fs.writeFileSync(BUNDLE_OUT, JSON.stringify(bundle));
fs.mkdirSync(path.dirname(RESOURCE_OUT), { recursive: true });
fs.copyFileSync(BUNDLE_OUT, RESOURCE_OUT);
console.log(`Generated ${bundle.quizzes.length} sets, ${totalQ} questions.`);
