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
  // --- new: collections ---
  { topic: "lists", q: (r) => ({ stem: `HashMap lookups are on average…`, opts: ["O(n)", "O(log n)", "O(1)", "O(n²)"], ans: 2 }) },
  { topic: "lists", q: (r) => ({ stem: `Which collection maintains insertion order by default?`, opts: ["HashSet", "TreeSet", "LinkedHashSet", "PriorityQueue"], ans: 2 }) },
  { topic: "lists", q: (r) => ({ stem: `ConcurrentModificationException is thrown when…`, opts: ["a thread reads a HashMap", "a collection is modified during iteration", "a null key is inserted", "the collection is empty"], ans: 1 }) },
  { topic: "lists", q: (r) => ({ stem: `LinkedList is best suited for…`, opts: ["random access by index", "frequent insert/delete at ends", "storing sorted unique keys", "read-heavy workloads"], ans: 1 }) },
  // --- new: generics ---
  { topic: "lists", q: (r) => ({ stem: `Generics provide…`, opts: ["runtime type checking", "compile-time type safety", "faster execution", "smaller bytecode"], ans: 1 }) },
  { topic: "lists", q: (r) => ({ stem: `What does <? extends Number> mean?`, opts: ["Number or any subclass", "Only Number", "Any type", "Number or any superclass"], ans: 0 }) },
  { topic: "lists", q: (r) => ({ stem: `Type erasure removes generic type info at…`, opts: ["compile time", "runtime", "link time", "class loading"], ans: 1 }) },
  { topic: "lists", q: (r) => ({ stem: `List<? super Integer> accepts…`, opts: ["List<Integer> only", "List<Number> and List<Object>", "List<String>", "List<int>"], ans: 1 }) },
  // --- new: exceptions ---
  { topic: "primitives", q: (r) => ({ stem: `Checked exceptions must be…`, opts: ["caught or declared with throws", "always caught", "ignored", "thrown manually"], ans: 0 }) },
  { topic: "primitives", q: (r) => ({ stem: `finally block executes…`, opts: ["only if an exception occurs", "only if no exception occurs", "always, regardless of exceptions", "only with try-with-resources"], ans: 2 }) },
  { topic: "primitives", q: (r) => ({ stem: `ArithmeticException is…`, opts: ["checked", "unchecked", "a compile error", "a warning"], ans: 1 }) },
  { topic: "primitives", q: (r) => ({ stem: `Multi-catch (catch A | B) was introduced in…`, opts: ["Java 6", "Java 7", "Java 8", "Java 11"], ans: 1 }) },
  // --- new: streams ---
  { topic: "arrays", q: (r) => ({ stem: `stream().filter() returns…`, opts: ["the same stream type", "a List", "an Optional", "a new Stream"], ans: 3 }) },
  { topic: "arrays", q: (r) => ({ stem: `stream().reduce(0, Integer::sum) returns…`, opts: ["Optional<Integer>", "int", "Integer", "Stream<Integer>"], ans: 2 }) },
  { topic: "arrays", q: (r) => ({ stem: `stream().map(x -> x * 2) transforms…`, opts: ["each element", "the stream size", "the stream type", "only odd elements"], ans: 0 }) },
  { topic: "arrays", q: (r) => ({ stem: `stream().collect(Collectors.toList()) returns…`, opts: ["a List<Object>", "a List with the stream's elements", "an array", "a Set"], ans: 1 }) },
  // --- new: lambdas ---
  { topic: "objects", q: (r) => ({ stem: `A lambda expression implements…`, opts: ["an abstract class", "a functional interface", "any interface", "a concrete class"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: `Which is a valid lambda?`, opts: ["(x) -> { return x; }", "x => x", "(x) x", "lambda x: x"], ans: 0 }) },
  { topic: "objects", q: (r) => ({ stem: `Method reference Class::new is shorthand for…`, opts: ["calling a static method", "creating an instance via constructor", "accessing a field", "overriding toString"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: `Predicate<String> is a functional interface that…`, opts: ["takes no args, returns String", "takes String, returns boolean", "takes boolean, returns String", "takes two Strings"], ans: 1 }) },
  // --- new: interfaces ---
  { topic: "objects", q: (r) => ({ stem: `An interface method without a body is implicitly…`, opts: ["private", "protected", "public abstract", "static"], ans: 2 }) },
  { topic: "objects", q: (r) => ({ stem: `Which keyword allows a class to implement multiple interfaces?`, opts: ["extends", "implements", "with", "mixin"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: `A default method in an interface provides…`, opts: ["abstract implementation", "a fallback implementation", "private access", "constructor logic"], ans: 1 }) },
  // --- new: enums ---
  { topic: "objects", q: (r) => ({ stem: `Enum values are implicitly…`, opts: ["public static final", "private static final", "protected volatile", "package-private"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: `An enum can have…`, opts: ["only constants", "constants and methods", "no constructors", "instance variables only via static"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: ` ordinal() returns…`, opts: ["the enum name as String", "the zero-based position", "the hash code", "the count of values"], ans: 1 }) },
  // --- new: inner classes ---
  { topic: "objects", q: (r) => ({ stem: `A non-static inner class has access to…`, opts: ["only static members of outer class", "only instance members of outer class", "both static and instance members", "no outer class members"], ans: 2 }) },
  { topic: "objects", q: (r) => ({ stem: `Anonymous inner classes are useful for…`, opts: ["reusable components", "one-off implementations of an interface", "performance optimization", "avoiding imports"], ans: 1 }) },
  { topic: "objects", q: (r) => ({ stem: `A local class defined inside a method can access…`, opts: ["any local variable", "only final or effectively final local variables", "no local variables", "only static variables"], ans: 1 }) },
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
  // --- new: decorators ---
  { topic:"objects", q:(r)=>({stem:`A decorator wraps a function to…`,opts:["change its name","modify or extend its behavior","delete it","make it private"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`@property creates a…`,opts:["class variable","read-only attribute","managed attribute with getter/setter","static method"],ans:2})},
  { topic:"objects", q:(r)=>({stem:`@staticmethod differs from @classmethod in that it…`,opts:["receives cls as first arg","receives no implicit first arg","can only be called on instances","is always private"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`@functools.lru_cache caches…`,opts:["all calls forever","recent calls based on arguments","only the last call","random results"],ans:1})},
  // --- new: generators ---
  { topic:"lists", q:(r)=>({stem:`A generator function uses…`,opts:["return with a list","yield keyword","async keyword","raise"],ans:1})},
  { topic:"lists", q:(r)=>({stem:`Generators are…`,opts:["eagerly evaluated","lazily evaluated","always faster than lists","immutable"],ans:1})},
  { topic:"lists", q:(r)=>({stem:`(x**2 for x in range(5)) creates…`,opts:["a list","a tuple","a generator expression","a set"],ans:2})},
  { topic:"lists", q:(r)=>({stem:`next(gen) on an exhausted generator raises…`,opts:["StopIteration","ValueError","IndexError","RuntimeError"],ans:0})},
  // --- new: context managers ---
  { topic:"objects", q:(r)=>({stem:`with open('f') as f uses a…`,opts:["decorator","context manager","lambda","metaclass"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`__enter__ returns…`,opts:["the file handle","True","None","the class"],ans:0})},
  { topic:"objects", q:(r)=>({stem:`__exit__ is called…`,opts:["on enter","on leave regardless of exceptions","only on success","never"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`contextlib.contextmanager lets you write…`,opts:["classes only","generator-based context managers","only async code","only file handlers"],ans:1})},
  // --- new: comprehensions ---
  { topic:"lists", q:(r)=>({stem:`[x*2 for x in range(5) if x%2==0] produces…`,opts:["[0,4,8]","[0,2,4,6,8]","[2,4,6,8,10]","[1,3,5]"],ans:0})},
  { topic:"lists", q:(r)=>({stem:`{k: v for k, v in items} creates a…`,opts:["list","set","tuple","dictionary"],ans:3})},
  { topic:"lists", q:(r)=>({stem:`Nested comprehension [[i*j for j in range(3)] for i in range(2)] produces…`,opts:["[[0,0,0],[0,1,2]]","[[0,1,2],[0,2,4]]","[0,1,2,0,2,4]","error"],ans:1})},
  { topic:"lists", q:(r)=>({stem:`A set comprehension uses…`,opts:["()","[]","{}","<>"],ans:2})},
  // --- new: async/await ---
  { topic:"objects", q:(r)=>({stem:`async def defines…`,opts:["a synchronous function","a coroutine function","a class","a module"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`await can only be used inside…`,opts:["any function","a regular function","an async function","a lambda"],ans:2})},
  { topic:"objects", q:(r)=>({stem:`asyncio.run() does…`,opts:["runs a coroutine synchronously","starts the event loop and runs a coroutine","blocks forever","returns a generator"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`asyncio.gather() runs tasks…`,opts:["sequentially","concurrently","only one at a time","in reverse order"],ans:1})},
  // --- new: modules ---
  { topic:"primitives", q:(r)=>({stem:`from math import sqrt allows…`,opts:["import math","sqrt() directly","math.sqrt() only","no usage"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`importlib.reload(mod) does…`,opts:["deletes the module","re-imports a modified module","freezes it","renames it"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`__name__ == "__main__" is used to…`,opts:["import the module","run code only when executed directly","define a class","enable async"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`pip installs packages from…`,opts:["the current directory only","PyPI","the system PATH","GitHub"],ans:1})},
  // --- new: exceptions ---
  { topic:"primitives", q:(r)=>({stem:`try/except/else/finally — else runs when…`,opts:["an exception occurs","no exception occurs","always","only with return"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`raise ValueError("msg") creates an exception with…`,opts:["no message","msg as the traceback","msg as the string value","msg as the type"],ans:2})},
  { topic:"primitives", q:(r)=>({stem:`except Exception as e binds…`,opts:["the exception type","the exception instance","the traceback","the line number"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`ExceptionGroup (3.11+) handles…`,opts:["a single error","multiple simultaneous exceptions","only TypeError","only syntax errors"],ans:1})},
  // --- extra: dicts ---
  { topic:"arrays", q:(r)=>({stem:`dict.get('k', default) returns default when…`,opts:["key exists","key is missing","dict is empty","always"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`dict.setdefault('k', v) inserts v only if…`,opts:["key already exists","key is missing","dict is new","v is None"],ans:1})},
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
  // --- new: templates ---
  { topic:"primitives", q:(r)=>({stem:`template<typename T> T max(T a, T b) is…`,opts:["a macro","a function template","a class","a variable"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`Template specialization allows…`,opts:["different behavior for specific types","faster compilation","no type checking","runtime polymorphism"],ans:0})},
  { topic:"primitives", q:(r)=>({stem:`constexpr functions are evaluated…`,opts:["only at runtime","at compile time when possible","never","only in debug builds"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`auto keyword deduces type at…`,opts:["runtime","compile time","link time","never"],ans:1})},
  // --- new: smart pointers ---
  { topic:"objects", q:(r)=>({stem:`std::unique_ptr provides…`,opts:["shared ownership","exclusive ownership","raw pointer arithmetic","garbage collection"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`std::shared_ptr uses…`,opts:["reference counting","garbage collection","manual delete","compile-time tracking"],ans:0})},
  { topic:"objects", q:(r)=>({stem:`std::weak_ptr breaks…`,opts:["memory","circular references","stack frames","templates"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`make_unique<T>() is preferred over new because…`,opts:["it's faster","it's exception-safe","it uses less memory","it's constexpr"],ans:1})},
  // --- new: STL ---
  { topic:"arrays", q:(r)=>({stem:`std::map stores keys in…`,opts:["insertion order","sorted order","reverse order","random order"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`std::unordered_map average lookup is…`,opts:["O(n)","O(log n)","O(1)","O(n log n)"],ans:2})},
  { topic:"arrays", q:(r)=>({stem:`std::deque differs from std::vector in…`,opts:["no random access","efficient front insertion","no iterators","fixed size"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`std::set automatically…`,opts:["sorts and deduplicates","preserves insertion order","allows duplicates","is always empty"],ans:0})},
  // --- new: move semantics ---
  { topic:"objects", q:(r)=>({stem:`std::move(x) does…`,opts:["copies x","casts x to rvalue reference","deletes x","prints x"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Move constructor should…`,opts:["deep copy resources","steal resources from the source","do nothing","throw an exception"], ans:1})},
  { topic:"objects", q:(r)=>({stem:`Rvalue references are declared with…`,opts:["T&","T&&","const T&","T*"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Perfect forwarding uses…`,opts:["T& only","T&& only","std::forward<T>()","std::move()"],ans:2})},
  // --- new: RAII ---
  { topic:"objects", q:(r)=>({stem:`RAII prevents resource leaks by…`,opts:["calling delete manually","tying resources to object lifetimes","using garbage collection","compiling faster"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`std::lock_guard uses RAII to…`,opts:["copy a mutex","automatically unlock a mutex","lock forever","create threads"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`ifstream destructor…`,opts:["leaves file open","closes the file","throws an error","does nothing"],ans:1})},
  // --- new: virtual functions ---
  { topic:"polymorphism", q:(r)=>({stem:`A pure virtual function is declared with…`,opts:["virtual only","= 0 suffix","override keyword","final keyword"],ans:1})},
  { topic:"polymorphism", q:(r)=>({stem:`A class with a pure virtual function is a…`,opts:["base class","abstract class","derived class","concrete class"],ans:1})},
  { topic:"polymorphism", q:(r)=>({stem:`final on a method prevents…`,opts:["override in derived classes","calling it","deleting it","copying it"],ans:0})},
  // --- new: lambdas ---
  { topic:"primitives", q:(r)=>({stem:`[](){}() is a…`,opts:["function declaration","lambda expression","macro","template"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`[=] captures variables by…`,opts:["reference","value","move","no capture"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`[&] captures variables by…`,opts:["value","reference","move","no capture"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`mutable on a lambda allows…`,opts:["modifying captured values by value","changing the lambda type","throwing exceptions","constexpr evaluation"],ans:0})},
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
  // --- new: pointers ---
  { topic:"primitives", q:(r)=>({stem:`int *p; p stores…`,opts:["an integer","a memory address","a function","a string"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`*p dereferences…`,opts:["the pointer's address","the value pointed to","the pointer itself","nothing"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`NULL pointer points to…`,opts:["address 0 (invalid)","the stack","the heap","the first variable"],ans:0})},
  { topic:"primitives", q:(r)=>({stem:`Pointer arithmetic p+1 advances by…`,opts:["1 byte","sizeof(*p) bytes","sizeof(p) bytes","4 bytes"],ans:1})},
  // --- new: memory allocation ---
  { topic:"primitives", q:(r)=>({stem:`malloc returns…`,opts:["initialized memory","void* to uninitialized memory","NULL always","a typed pointer"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`free(ptr) does…`,opts:["sets ptr to NULL","deallocates memory pointed to by ptr","zeros the memory","nothing"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`Memory leak occurs when…`,opts:["malloc fails","allocated memory is never freed","stack overflows","a pointer is NULL"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`calloc(n, sz) differs from malloc in…`,opts:["it's faster","it initializes memory to zero","it allocates on stack","it returns a typed pointer"],ans:1})},
  // --- new: structs ---
  { topic:"objects", q:(r)=>({stem:`struct Point { int x; int y; }; sizeof(Point) is…`,opts:["4","8","12","depends on compiler"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Accessing struct member: p.x means…`,opts:["dereference p","access x through pointer p","access field x of struct p","both a and c"],ans:3})},
  { topic:"objects", q:(r)=>({stem:`-> operator is shorthand for…`,opts:["(*p).member","p.member","&p.member","(*p).member only"],ans:0})},
  { topic:"objects", q:(r)=>({stem:`typedef struct { ... } Name; allows…`,opts:["heap allocation","using Name without struct keyword","making the struct private","inheriting"],ans:1})},
  // --- new: function pointers ---
  { topic:"objects", q:(r)=>({stem:`int (*fp)(int) declares…`,opts:["a function returning int*","a pointer to a function taking int, returning int","an array of functions","a function pointer variable"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`qsort requires a comparator function returning…`,opts:["bool","int","void*","size_t"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Callback functions enable…`,opts:["faster code","flexible behavior via function pointers","type safety","stack allocation"],ans:1})},
  // --- new: preprocessor ---
  { topic:"primitives", q:(r)=>({stem:`#define SQUARE(x) ((x)*(x)) — extra parens prevent…`,opts:["compilation errors","macro argument grouping bugs","type errors","memory leaks"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`#include <stdio.h> vs "stdio.h" — quotes search…`,opts:["system directories first","current directory first","nowhere","only system dirs"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`#ifdef DEBUG guards…`,opts:["runtime checks","compile-time conditional code","type definitions","linker symbols"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`#pragma once does…`,opts:["includes header once","prevents multiple inclusion of a header file","enables optimizations","defines a macro"],ans:1})},
  // --- new: bit operations ---
  { topic:"primitives", q:(r)=>({stem:`x & (x - 1) clears the…`,opts:["highest set bit","lowest set bit","all bits","sign bit"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`x ^ x evaluates to…`,opts:["x","0","1","undefined"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`Left shift x << 1 is equivalent to…`,opts:["x / 2","x * 2","x + 2","x - 2"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`Bitwise OR (|) sets a bit when…`,opts:["both bits are 0","at least one bit is 1","both bits are 1","exactly one bit is 1"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`Unsigned right shift (>>>) in Java fills with…`,opts:["the sign bit","zeros","ones","undefined"],ans:1})},
  // --- extra: arrays ---
  { topic:"arrays", q:(r)=>({stem:`sizeof(arr)/sizeof(arr[0]) gives…`,opts:["the first element","the number of elements","the total bytes","the address"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`char s[] = "hi"; sizeof(s) is…`,opts:["2","3","4","undefined"],ans:1})},
];

// =====================================================================
// JAVASCRIPT / NODE.JS
// =====================================================================
const jsStarter = `// read input with require('fs').readFileSync('/dev/stdin', 'utf8')\n`;
const jsOJ = [
  { topic:"arrays", title:"Array Sum", make(rng,d){
      const n=d===0?5:7; const a=Array.from({length:n},()=>int(rng,-20,80));
      return q(`Read n then n integers; print their sum.`,
        jsStarter, cases([[`${n}\n${a.join(' ')}`, String(a.reduce((x,y)=>x+y,0))], ...more(rng, ()=>{const k=int(rng,3,9);const b=Array.from({length:k},()=>int(rng,-30,100));return [`${k}\n${b.join(' ')}`,String(b.reduce((x,y)=>x+y,0))];})])); } },
  { topic:"strings", title:"Word Count", make(rng){
      const ws=Array.from({length:int(rng,3,7)},()=>pick(rng,WORDS));
      return q(`Read one line; print the number of words.`,
        jsStarter, cases([[ws.join(' '), String(ws.length)], ...more(rng, ()=>{const v=shuffle(rng,WORDS).slice(0,int(rng,3,6));return [v.join(' '), String(v.length)];})])); } },
  { topic:"objects", title:"Property Counter", make(rng){
      const n=int(rng,2,6); const keys=shuffle(rng,WORDS).slice(0,n);
      return q(`Read n, then n "key value" lines. Print the number of keys.`,
        jsStarter, cases([[`${n}\n${keys.map(k=>`${k} ${int(rng,1,99)}`).join('\n')}`, String(n)], ...more(rng,()=>{const k=int(rng,2,5);const kk=shuffle(rng,WORDS).slice(0,k);return [`${k}\n${kk.map(x=>`${x} ${int(rng,1,99)}`).join('\n')}`,String(k)];})])); } },
  { topic:"functions", title:"Higher-Order Filter", make(rng){
      const a=Array.from({length:int(rng,5,8)},()=>int(rng,-10,30));
      const evens=a.filter(x=>x%2===0);
      return q(`Read n then n integers; print the even numbers, space-separated.`,
        jsStarter, cases([[`${a.length}\n${a.join(' ')}`, evens.length?evens.join(' '):''], ...more(rng,()=>{const b=Array.from({length:int(rng,4,8)},()=>int(rng,-20,40));const e=b.filter(x=>x%2===0);return [`${b.length}\n${b.join(' ')}`,e.length?e.join(' '):''];})])); } },
  { topic:"search-sort", title:"Sort Descending", make(rng){
      const a=Array.from({length:int(rng,4,8)},()=>int(rng,1,100));
      const s=[...a].sort((x,y)=>y-x);
      return q(`Read n then n integers; print them sorted descending, space-separated.`,
        jsStarter, cases([[`${a.length}\n${a.join(' ')}`, s.join(' ')], ...more(rng,()=>{const b=Array.from({length:int(rng,4,8)},()=>int(rng,0,99));return [`${b.length}\n${b.join(' ')}`,[...b].sort((x,y)=>y-x).join(' ')];})])); } },
  { topic:"primitives", title:"FizzBuzz", make(rng){
      const n=pick(rng,[15,20,30]);
      const out=[];for(let i=1;i<=n;i++){if(i%15===0)out.push('FizzBuzz');else if(i%3===0)out.push('Fizz');else if(i%5===0)out.push('Buzz');else out.push(String(i));}
      return q(`Read N; print FizzBuzz for 1..N, one per line.`,
        jsStarter, cases([[String(n), out.join('\n')], ...more(rng,()=>{const k=pick(rng,[10,15,20,25]);const o=[];for(let i=1;i<=k;i++){if(i%15===0)o.push('FizzBuzz');else if(i%3===0)o.push('Fizz');else if(i%5===0)o.push('Buzz');else o.push(String(i));}return [String(k),o.join('\n')];})])); } },
  { topic:"recursion", title:"Factorial", make(rng){
      const n=int(rng,1,12);
      let f=1;for(let i=2;i<=n;i++)f*=i;
      return q(`Read N; print N! (factorial).`,
        jsStarter, cases([[String(n),String(f)], ...more(rng,()=>{const k=int(rng,1,10);let r=1;for(let i=2;i<=k;i++)r*=i;return [String(k),String(r)];})])); } },
];
const jsBugs = [
  { t:"Missing await on async call", lines:(r)=>[`async function getData() {`,`  const result = fetch('/api');`,`  return result.json();`,`}`], bugLine: 1 },
  { t:"== instead of ===", lines:(r)=>[`if (value == null) {`,`  return undefined;`,`}`], bugLine: 0 },
  { t:"var instead of const/let", lines:(r)=>[`var name = "test";`,`console.log(name);`], bugLine: 0 },
  { t:"Off-by-one array slice", lines:(r)=>[`const first = arr.slice(0, arr.length);`,`console.log(first);`], bugLine: 0 },
  { t:"Missing return in arrow", lines:(r)=>[`const double = x => x * 2;`,`console.log(double(5));`], bugLine: 0 },
  { t:"Mutation of parameter", lines:(r)=>[`function addItem(list, item) {`,`  list.push(item);`,`  return list;`,`}`], bugLine: 1 },
];
const jsMcq = [
  { topic:"primitives", q:(r)=>({stem:`typeof null in JavaScript returns…`,opts:["'null'","'object'","'undefined'","'boolean'"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`[1,2,3].length is…`,opts:["2","3","4","undefined"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Object.keys({a:1,b:2}) returns…`,opts:["['a','b']","[1,2]","{a:1,b:2}","undefined"],ans:0})},
  { topic:"functions", q:(r)=>({stem:`Arrow functions capture 'this' from…`,opts:["the function itself","the enclosing scope","the global object","nowhere"],ans:1})},
  { topic:"strings", q:(r)=>({stem:`'hello'.slice(1,3) returns…`,opts:["'el'","'ell'","'hel'","'lo'"],ans:0})},
  { topic:"search-sort", q:(r)=>({stem:`Array.sort() sorts by…`,opts:["numeric order","Unicode code points","custom comparator only","descending"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`NaN === NaN evaluates to…`,opts:["true","false","undefined","throws"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`[...arr] creates a…`,opts:["deep copy","shallow copy","reference","immutable array"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`const {x} = {x:1} extracts…`,opts:["x=1","x={1}","undefined","error"],ans:0})},
  { topic:"functions", q:(r)=>({stem:`Promise.all() resolves when…`,opts:["first resolves","all resolve","any resolves","none resolve"],ans:1})},
  // --- new: promises ---
  { topic:"functions", q:(r)=>({stem:`new Promise((res,rej) => {}) is in…`,opts:["resolved state","rejected state","pending state","settled state"],ans:2})},
  { topic:"functions", q:(r)=>({stem:`promise.catch(fn) is shorthand for…`,opts:["promise.then(fn)","promise.then(null, fn)","promise.finally(fn)","promise.all(fn)"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`Promise.race() resolves/rejects with…`,opts:["the first settled promise","all promises","the last promise","the fastest rejection"],ans:0})},
  { topic:"functions", q:(r)=>({stem:`Promise.allSettled() resolves when…`,opts:["first settles","all settle (resolve or reject)","any resolves","any rejects"],ans:1})},
  // --- new: async/await ---
  { topic:"functions", q:(r)=>({stem:`await pauses execution until…`,opts:["the function returns","the promise settles","the next tick","the microtask queue empties"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`async function always returns a…`,opts:["plain value","Promise","generator","undefined"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`Unhandled promise rejection causes…`,opts:["silent failure","unhandledrejection event or crash","retry","undefined"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`Top-level await works in…`,opts:["any .js file","ES modules (.mjs)","only CommonJS","only Node.js"],ans:1})},
  // --- new: closures ---
  { topic:"functions", q:(r)=>({stem:`A closure captures…`,opts:["only local variables","variables from its lexical scope","global variables only","nothing"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`IIFE stands for…`,opts:["Immediately Invoked Function Expression","Internal Interface for Functions","Indexed Iterator for Functions","Inline Implementation"],ans:0})},
  { topic:"functions", q:(r)=>({stem:`for (var i=0;...) setTimeout(()=>log(i)) prints…`,opts:["0,1,2","3,3,3","undefined","error"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`Changing to 'let' in the loop above prints…`,opts:["3,3,3","0,1,2","undefined","error"],ans:1})},
  // --- new: prototypes ---
  { topic:"objects", q:(r)=>({stem:`__proto__ links to…`,opts:["the constructor","the parent object's prototype","the global object","nothing"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`Object.create(proto) creates…`,opts:["a copy of proto","an object with proto as [[Prototype]]","a new class","a frozen object"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`class Dog extends Animal uses…`,opts:["prototypal inheritance","classical inheritance","mixin pattern","composition only"],ans:0})},
  { topic:"objects", q:(r)=>({stem:`hasOwnProperty checks…`,opts:["prototype chain","own properties only","both","neither"],ans:1})},
  // --- new: modules ---
  { topic:"primitives", q:(r)=>({stem:`export default function allows…`,opts:["multiple default exports","one unnamed export per module","named only","no export"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`import {x} from './mod' destructures…`,opts:["the default export","named exports","the module object","nothing"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`import './side' executes…`,opts:["nothing","the module's side effects only","a default export","all named exports"],ans:1})},
  { topic:"primitives", q:(r)=>({stem:`CommonJS uses…`,opts:["import/export","require()/module.exports","define/require","System.import"],ans:1})},
  // --- new: destructuring ---
  { topic:"arrays", q:(r)=>({stem:`const [a, , b] = [1,2,3] assigns…`,opts:["a=1,b=2","a=1,b=3","a=2,b=3","error"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`const {x: y} = {x: 5} assigns y = …`,opts:["'x'","5","undefined","error"],ans:1})},
  { topic:"arrays", q:(r)=>({stem:`function foo([a,b]=[]) destructures…`,opts:["arguments","the first parameter as an array","global scope","nothing"],ans:1})},
  // --- new: spread/rest ---
  { topic:"arrays", q:(r)=>({stem:`Math.max(...arr) spreads…`,opts:["arr into individual arguments","arr into a string","arr into an object","nothing"],ans:0})},
  { topic:"objects", q:(r)=>({stem:`{...obj1, ...obj2} merges…`,opts:["arrays","objects (last wins on conflict)","functions","strings"],ans:1})},
  { topic:"functions", q:(r)=>({stem:`function f(...args) collects…`,opts:["named parameters","rest arguments into an array","only the first argument","nothing"],ans:1})},
  // --- new: optional chaining ---
  { topic:"objects", q:(r)=>({stem:`obj?.prop?.method() returns…`,opts:["always a value","undefined if any part is null/undefined","throws an error","the method itself"],ans:1})},
  { topic:"objects", q:(r)=>({stem:`arr?.[0] safely accesses…`,opts:["the first element or undefined if arr is nullish","always the first element","the array length","nothing"],ans:0})},
  { topic:"objects", q:(r)=>({stem:`obj?.foo?.() safely calls…`,opts:["a method that always exists","a method only if obj and foo are not nullish","any function","nothing"],ans:1})},
];

// =====================================================================
// CYBERSECURITY
// =====================================================================
const cyberMcq = [
  { topic:"networks", q:(r)=>({stem:`Which protocol encrypts web traffic?`,opts:["HTTP","FTP","HTTPS","SMTP"],ans:2})},
  { topic:"networks", q:(r)=>({stem:`A firewall primarily…`,opts:["encrypts data","monitors incoming/outgoing traffic","backs up data","runs antivirus"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`AES is classified as…`,opts:["symmetric encryption","asymmetric encryption","hashing algorithm","key exchange"],ans:0})},
  { topic:"encryption", q:(r)=>({stem:`RSA is classified as…`,opts:["symmetric encryption","asymmetric encryption","hashing algorithm","digital signature"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`SQL injection targets…`,opts:["the network layer","database queries","file system","CPU registers"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`Cross-Site Scripting (XSS) injects…`,opts:["SQL commands","malicious scripts","viruses","network packets"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`Strong passwords should include…`,opts:["only lowercase letters","uppercase, lowercase, numbers, symbols","just numbers","the username"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`Multi-Factor Authentication requires…`,opts:["one password","two or more verification methods","biometrics only","a hardware key"],ans:1})},
  { topic:"networks", q:(r)=>({stem:`DNS translates…`,opts:["IP to MAC","domain names to IP addresses","ports to protocols","HTTP to HTTPS"],ans:1})},
  { topic:"networks", q:(r)=>({stem:`VPN creates a…`,opts:["public connection","encrypted tunnel","direct cable link","wireless bridge"],ans:1})},
  // --- new: authentication ---
  { topic:"best-practices", q:(r)=>({stem:`OAuth 2.0 is used for…`,opts:["password hashing","delegated authorization","symmetric encryption","file integrity"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`Session tokens should be…`,opts:["stored in localStorage","regenerated after login","sent in URLs","unchanged forever"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`Password hashing with bcrypt uses…`,opts:["MD5","a salted one-way function","base64 encoding","plain SHA-256"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`JWT stands for…`,opts:["Java Web Token","JSON Web Token","JavaScript Wrapper Token","Joint Wire Transfer"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`OAuth refresh tokens are used to…`,opts:["replace access tokens without re-authentication","encrypt passwords","generate API keys","hash data"],ans:0})},
  { topic:"best-practices", q:(r)=>({stem:`Rate limiting prevents…`,opts:["SQL injection","brute-force attacks","XSS","CSRF"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`CSRF attacks target…`,opts:["database queries","authenticated users' browsers","network cables","password hashes"],ans:1})},
  // --- new: authorization ---
  { topic:"best-practices", q:(r)=>({stem:`RBAC stands for…`,opts:["Random Based Access Control","Role-Based Access Control","Remote Browser Authentication","Revolving Certificate Authority"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`Principle of least privilege means…`,opts:["users get maximum access","users get only the minimum access needed","admins have no access","everyone is equal"],ans:1})},
  { topic:"best-practices", q:(r)=>({stem:`ACL stands for…`,opts:["Access Control List","Authenticated Certificate Layer","Advanced Encryption Lock","Application Code Locator"],ans:0})},
  { topic:"best-practices", q:(r)=>({stem:`Separation of duties prevents…`,opts:["performance issues","a single person from completing a critical task alone","phishing","DNS attacks"], ans:1})},
  // --- new: cryptography ---
  { topic:"encryption", q:(r)=>({stem:`SHA-256 produces a…`,opts:["128-bit hash","256-bit hash","512-bit hash","variable-length hash"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`A digital signature provides…`,opts:["encryption only","authentication and non-repudiation","key exchange","compression"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`A salt in password hashing…`,opts:["makes passwords shorter","adds randomness before hashing","replaces the password","speeds up hashing"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`Diffie-Hellman is used for…`,opts:["encryption","key exchange","hashing","digital signatures"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`An IV (Initialization Vector) is needed for…`,opts:["hash functions","block cipher modes like CBC","RSA key generation","password storage"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`HMAC provides…`,opts:["encryption","message authentication","key exchange","compression"],ans:1})},
  { topic:"encryption", q:(r)=>({stem:`Public key infrastructure (PKI) relies on…`,opts:["symmetric keys","certificate authorities","firewalls","VPNs"],ans:1})},
  // --- new: network security ---
  { topic:"networks", q:(r)=>({stem:`A DMZ is a…`,opts:["encrypted zone","buffer zone between internal and external networks","password manager","backup system"],ans:1})},
  { topic:"networks", q:(r)=>({stem:`TLS 1.3 handshake completes in…`,opts:["1 round trip","2 round trips","3 round trips","4 round trips"],ans:0})},
  { topic:"networks", q:(r)=>({stem:`Port 443 is used by…`,opts:["HTTP","FTP","HTTPS","SSH"],ans:2})},
  { topic:"networks", q:(r)=>({stem:`IDS vs IPS — IPS can…`,opts:["only detect threats","detect AND block threats","only log traffic","only encrypt data"],ans:1})},
  { topic:"networks", q:(r)=>({stem:`Network segmentation…`,opts:["combines all devices","isolates network segments to limit breach impact","speeds up DNS","removes firewalls"],ans:1})},
  { topic:"networks", q:(r)=>({stem:`A man-in-the-middle attack intercepts…`,opts:["passwords only","communication between two parties","hard drives","CPU cycles"],ans:1})},
  { topic:"networks", q:(r)=>({stem:`DNSSEC protects against…`,opts:["DDoS","DNS spoofing","SQL injection","buffer overflow"],ans:1})},
  // --- new: social engineering ---
  { topic:"vulnerabilities", q:(r)=>({stem:`Phishing targets…`,opts:["databases","human psychology","network cables","CPU registers"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`Pretexting involves…`,opts:["encrypting data","creating a fabricated scenario to obtain information","brute-forcing passwords","scanning ports"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`Tailgating exploits…`,opts:["software bugs","human trust to gain physical access","network protocols","password policies"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`A rubber duck attack is a form of…`,opts:["network sniffing","social engineering via impersonation","SQL injection","buffer overflow"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`Vishing is…`,opts:["video phishing","voice-based social engineering","visual cryptography","virtual networking"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`Whaling targets…`,opts:["ordinary users","high-profile individuals like executives","network devices","DNS servers"],ans:1})},
  { topic:"vulnerabilities", q:(r)=>({stem:`Watering hole attacks compromise…`,opts:["water supplies","websites frequently visited by the target","password databases","encryption keys"],ans:1})},
];

// =====================================================================
// WEB DEV
// =====================================================================
const webMcq = [
  { topic:"html", q:(r)=>({stem:`The correct HTML for a hyperlink is…`,opts:["<link>","<a href='url'>","<href>","<url>"],ans:1})},
  { topic:"html", q:(r)=>({stem:`HTML5 semantic tags include…`,opts:["<div>","<span>","<article>","<b>"],ans:2})},
  { topic:"css", q:(r)=>({stem:`CSS specificity order (low to high) is…`,opts:["class > element > id","element > class > id","id > class > element","element > id > class"],ans:1})},
  { topic:"css", q:(r)=>({stem:`display: flex creates a…`,opts:["block layout","inline layout","flexbox layout","grid layout"],ans:2})},
  { topic:"javascript", q:(r)=>({stem:`addEventListener() attaches a…`,opts:["HTML tag","CSS rule","event handler","database query"],ans:2})},
  { topic:"javascript", q:(r)=>({stem:`document.querySelector('.btn') selects by…`,opts:["id","class","tag name","attribute"],ans:1})},
  { topic:"html", q:(r)=>({stem:`The <img> tag requires which attribute?`,opts:["src","href","url","link"],ans:0})},
  { topic:"css", q:(r)=>({stem:`position: absolute positions relative to…`,opts:["the viewport","the document body","the nearest positioned ancestor","the parent element"],ans:2})},
  { topic:"javascript", q:(r)=>({stem:`fetch() returns a…`,opts:["string","Promise","JSON object","void"],ans:1})},
  { topic:"html", q:(r)=>({stem:`The <form> action attribute defines…`,opts:["method","submit URL","input types","validation"],ans:1})},
  // --- new: responsive design ---
  { topic:"css", q:(r)=>({stem:`@media (max-width: 768px) targets…`,opts:["printers","mobile devices","large screens","all devices"],ans:1})},
  { topic:"css", q:(r)=>({stem:`viewport meta tag controls…`,opts:["font size only","page scaling on mobile devices","image loading","JavaScript execution"],ans:1})},
  { topic:"css", q:(r)=>({stem:`min-width in media queries means…`,opts:["apply if screen ≥ width","apply if screen ≤ width","apply always","apply on print"],ans:0})},
  { topic:"css", q:(r)=>({stem:`Responsive images use…`,opts:["<img srcset>","<img responsive>","<img stretch>","CSS only"],ans:0})},
  { topic:"css", q:(r)=>({stem:`Fluid typography uses…`,opts:["fixed px values","vw units or clamp()","only em units","no sizing"],ans:1})},
  // --- new: CSS Grid ---
  { topic:"css", q:(r)=>({stem:`display: grid creates a…`,opts:["flex container","grid container","block container","table container"],ans:1})},
  { topic:"css", q:(r)=>({stem:`grid-template-columns: repeat(3, 1fr) creates…`,opts:["3 equal-width columns","3 fixed-width columns","1 column","9 columns"],ans:0})},
  { topic:"css", q:(r)=>({stem:`grid-gap adds space between…`,opts:["pages","grid items","columns only","the viewport"],ans:1})},
  { topic:"css", q:(r)=>({stem:`An element spanning 2 columns uses…`,opts:["grid-column: span 2","grid-col: 2","column-span: 2","col-span: 2"],ans:0})},
  { topic:"css", q:(r)=>({stem:`fr unit in grid means…`,opts:["fixed pixels","a fraction of available space","font ratio","free rows"],ans:1})},
  // --- new: flexbox ---
  { topic:"css", q:(r)=>({stem:`justify-content aligns items…`,opts:["along the cross axis","along the main axis","vertically only","horizontally only"],ans:1})},
  { topic:"css", q:(r)=>({stem:`align-items aligns items…`,opts:["along the main axis","along the cross axis","diagonally","only in grids"],ans:1})},
  { topic:"css", q:(r)=>({stem:`flex-direction: row-reverse does…`,opts:["reverses item order and direction","removes items","adds a scrollbar","hides items"],ans:0})},
  { topic:"css", q:(r)=>({stem:`flex-wrap: wrap allows items to…`,opts:["stay on one line","wrap to the next line if needed","animate","disappear"],ans:1})},
  { topic:"css", q:(r)=>({stem:`flex: 1 on a child means…`,opts:["take up remaining space","shrink to 1px","no flexing","fixed width"],ans:0})},
  // --- new: DOM manipulation ---
  { topic:"javascript", q:(r)=>({stem:`document.createElement() creates a…`,opts:["CSS rule","DOM element","string","event"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`element.appendChild() does…`,opts:["removes a child","adds a node as the last child","replaces all children","clones the element"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`element.textContent sets…`,opts:["innerHTML","the text content (no HTML parsing)","CSS styles","event listeners"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`element.classList.toggle('active') does…`,opts:["always adds","always removes","adds if missing, removes if present","renames the class"],ans:2})},
  { topic:"javascript", q:(r)=>({stem:`element.setAttribute('disabled', '') does…`,opts:["enables the element","disables the element","removes the attribute","sets a data attribute"],ans:1})},
  // --- new: events ---
  { topic:"javascript", q:(r)=>({stem:`event.preventDefault() stops…`,opts:["event propagation","the default browser action","the element from rendering","all handlers"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`event.stopPropagation() stops…`,opts:["the default action","event bubbling up the DOM","the current handler","only click events"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`addEventListener uses…`,opts:["capture phase only","bubble phase only","both capture and bubble phases","neither"], ans:2})},
  { topic:"javascript", q:(r)=>({stem:`'input' event fires on…`,opts:["form submit only","every value change in an input element","page load","mouse click"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`'change' event fires when…`,opts:["every keystroke","the element loses focus after value changed","on mouseover","on page load"],ans:1})},
  // --- new: storage ---
  { topic:"javascript", q:(r)=>({stem:`localStorage persists data…`,opts:["for the session only","until explicitly cleared","for 1 hour","until the tab closes"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`sessionStorage clears when…`,opts:["the browser closes","the tab/window closes","after 30 minutes","never"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`localStorage.setItem('k','v') stores…`,opts:["an object","a string","a number","a function"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`JSON.stringify() is needed to store…`,opts:["strings","objects and arrays in localStorage","numbers","booleans"],ans:1})},
  // --- new: fetch API ---
  { topic:"javascript", q:(r)=>({stem:`fetch() defaults to…`,opts:["POST","PUT","GET","DELETE"],ans:2})},
  { topic:"javascript", q:(r)=>({stem:`response.json() returns a…`,opts:["string","Promise that resolves to JSON","raw bytes","HTML"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`fetch('/api', {method:'POST', body:...}) sends a…`,opts:["GET request","POST request","DELETE request","OPTIONS request"],ans:1})},
  { topic:"javascript", q:(r)=>({stem:`fetch error handling uses…`,opts:[".catch() only","check response.ok or .catch()","try/catch with fetch directly","response.status === 200"], ans:1})},
];

// =====================================================================
// MATH
// =====================================================================
const mathMcq = [
  { topic:"logic", q:(r)=>({stem:`NOT (true AND false) evaluates to…`,opts:["true","false","undefined","error"],ans:0})},
  { topic:"logic", q:(r)=>({stem:`De Morgan's Law: NOT(A OR B) equals…`,opts:["NOT A OR NOT B","NOT A AND NOT B","A AND B","NOT A OR B"],ans:1})},
  { topic:"sets", q:(r)=>({stem:`{1,2,3} UNION {2,3,4} equals…`,opts:["{1,2,3}","{2,3}","{1,2,3,4}","{1,4}"],ans:2})},
  { topic:"sets", q:(r)=>({stem:`{1,2,3} INTERSECT {2,3,4} equals…`,opts:["{1,2,3,4}","{2,3}","{1,4}","{}"],ans:1})},
  { topic:"probability", q:(r)=>({stem:`P(A OR B) = P(A) + P(B) - …`,opts:["P(A AND B)","P(A) × P(B)","P(A | B)","P(B | A)"],ans:0})},
  { topic:"probability", q:(r)=>({stem:`Coin flip: P(heads) = …`,opts:["0","0.25","0.5","1"],ans:2})},
  { topic:"combinatorics", q:(r)=>({stem:`5! equals…`,opts:["25","60","120","720"],ans:2})},
  { topic:"combinatorics", q:(r)=>({stem:`C(5,2) equals…`,opts:["5","10","20","25"],ans:1})},
  { topic:"algebra", q:(r)=>({stem:`Solve: 2x + 3 = 7. x = …`,opts:["1","2","3","4"],ans:1})},
  { topic:"algebra", q:(r)=>({stem:`The quadratic formula solves…`,opts:["linear equations","systems of equations","quadratic equations","exponential equations"],ans:2})},
  // --- new: statistics ---
  { topic:"probability", q:(r)=>({stem:`Mean of {2,4,6} is…`,opts:["3","4","5","12"],ans:1})},
  { topic:"probability", q:(r)=>({stem:`Median of {1,3,5,7} is…`,opts:["3","4","5","6"],ans:1})},
  { topic:"probability", q:(r)=>({stem:`Mode of {1,2,2,3} is…`,opts:["1","2","3","1.5"],ans:1})},
  { topic:"probability", q:(r)=>({stem:`Standard deviation measures…`,opts:["the average","the spread or dispersion of data","the midpoint","the sum"],ans:1})},
  { topic:"probability", q:(r)=>({stem:`Variance is the square of…`,opts:["mean","median","standard deviation","mode"],ans:2})},
  { topic:"probability", q:(r)=>({stem:`Normal distribution is symmetric about…`,opts:["zero","the median = mean","the mode only","the maximum"],ans:1})},
  // --- new: linear algebra basics ---
  { topic:"algebra", q:(r)=>({stem:`A 2×2 identity matrix is…`,opts:["[[1,0],[0,1]]","[[1,1],[1,1]]","[[0,1],[1,0]]","[[2,0],[0,2]]"],ans:0})},
  { topic:"algebra", q:(r)=>({stem:`Determinant of [[a,0],[0,d]] is…`,opts:["a+d","a-d","ad","a/d"],ans:2})},
  { topic:"algebra", q:(r)=>({stem:`Matrix multiplication AB requires…`,opts:["same dimensions","A columns = B rows","A rows = B rows","any dimensions"],ans:1})},
  { topic:"algebra", q:(r)=>({stem:`det(AB) equals…`,opts:["det(A)+det(B)","det(A)×det(B)","det(A)/det(B)","det(A)-det(B)"],ans:1})},
  { topic:"algebra", q:(r)=>({stem:`A matrix is invertible when its determinant is…`,opts:["zero","one","non-zero","negative"],ans:2})},
  { topic:"algebra", q:(r)=>({stem:`Eigenvalue λ satisfies…`,opts:["Av = v","Av = λv","Aλ = v","Av = 0"],ans:1})},
  // --- new: number theory ---
  { topic:"combinatorics", q:(r)=>({stem:`GCD(12, 8) equals…`,opts:["2","4","6","24"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`LCM(4, 6) equals…`,opts:["10","12","24","2"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`A prime number has exactly…`,opts:["0 factors","1 factor","2 factors (1 and itself)","3 factors"],ans:2})},
  { topic:"combinatorics", q:(r)=>({stem:`Modular arithmetic: 17 mod 5 = …`,opts:["2","3","4","1"],ans:0})},
  { topic:"combinatorics", q:(r)=>({stem:`Fermat's Little Theorem: a^(p-1) mod p = …`,opts:["0","1","p","a"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`Euclidean algorithm computes…`,opts:["LCM","GCD","prime factorization","square root"],ans:1})},
  // --- new: graph theory basics ---
  { topic:"combinatorics", q:(r)=>({stem:`A tree with n nodes has…`,opts:["n edges","n-1 edges","n+1 edges","2n edges"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`BFS traversal uses…`,opts:["a stack","a queue","recursion","a heap"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`DFS traversal uses…`,opts:["a queue","a stack (or recursion)","a heap","a set"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`Dijkstra's algorithm finds…`,opts:["longest path","shortest path from a source","maximum flow","minimum spanning tree"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`A complete graph on n nodes has…`,opts:["n edges","n(n-1)/2 edges","n² edges","2n edges"],ans:1})},
  { topic:"combinatorics", q:(r)=>({stem:`An Eulerian circuit visits every…`,opts:["vertex exactly once","edge exactly once","vertex at least once","edge at most once"],ans:1})},
  // --- extra ---
  { topic:"logic", q:(r)=>({stem:`P XOR Q is true when…`,opts:["both true","both false","exactly one is true","neither"],ans:2})},
  { topic:"sets", q:(r)=>({stem:`|A × B| (Cartesian product size) equals…`,opts:["|A| + |B|","|A| × |B|","|A| / |B|","|A| - |B|"],ans:1})},
  { topic:"probability", q:(r)=>({stem:`Conditional probability P(A|B) = …`,opts:["P(A AND B) / P(B)","P(A) / P(B)","P(A AND B)","P(A) + P(B)"],ans:0})},
  { topic:"algebra", q:(r)=>({stem:`log₂(8) equals…`,opts:["2","3","4","8"],ans:1})},
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
  javascript: ["node"], cybersecurity: null, webdev: null, math: null,
};

// ---------- assembly ----------
function assembleLanguage(lang, cfg, startN) {
  const ojPool = lang === "java" ? javaOJ : lang === "python" ? pyOJ : lang === "cpp" ? cppOJ : lang === "c" ? cOJ : lang === "javascript" ? jsOJ : [];
  const bugPool = lang === "java" ? javaBugs : lang === "python" ? pyBugs : lang === "cpp" ? cppBugs : lang === "c" ? cBugs : lang === "javascript" ? jsBugs : [];
  const mcqPool = lang === "java" ? javaMcq : lang === "python" ? pyMcq : lang === "cpp" ? cppMcq : lang === "c" ? cMcq : lang === "javascript" ? jsMcq : lang === "cybersecurity" ? cyberMcq : lang === "webdev" ? webMcq : lang === "math" ? mathMcq : [];

  const quizzes = [];
  let n = startN || 0;
  for (const band of cfg.bands) {           // [{label:'Foundations',count:18}]
    for (let i = 0; i < band.count; i++) {
      const rng = mulberry32(cfg.seedBase + n * 7919 + hashStr(band.label));
      const diff = band.diff;                // 0 easy, 1 medium, 2 hard
      const topic = cfg.topics[n % cfg.topics.length];
      const quizId = `${lang}-${String(n + 1).padStart(3, "0")}`;

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
  // Java: expanded to 200+ sets
  { lang:"java",   bands:[{label:"Essentials",diff:0,count:40}], topics:["objects","arrays","strings","primitives","lists"], seedBase:1101 },
  { lang:"java",   bands:[{label:"Foundations",diff:0,count:18},{label:"Intermediate",diff:1,count:20},{label:"Advanced",diff:2,count:12}],
    topics:["objects","arrays","arrays","strings","lists","search-sort","recursion","arrays2d","polymorphism","inheritance","primitives","objects"] , seedBase:101 },
  { lang:"java",   bands:[{label:"Easy",diff:0,count:30},{label:"Medium",diff:1,count:30},{label:"Hard",diff:2,count:20}],
    topics:["objects","arrays","strings","primitives","lists","search-sort","recursion","arrays2d","polymorphism","inheritance"], seedBase:501 },
  // Python: expanded to 130+ sets
  { lang:"python", bands:[{label:"Essentials",diff:0,count:25}], topics:["lists","strings","primitives","objects","arrays"], seedBase:1201 },
  { lang:"python", bands:[{label:"Foundations",diff:0,count:7},{label:"Intermediate",diff:1,count:8},{label:"Advanced",diff:2,count:5}],
    topics:["lists","strings","objects","arrays","recursion","search-sort","arrays2d","primitives","lists","strings"], seedBase:202 },
  { lang:"python", bands:[{label:"Easy",diff:0,count:20},{label:"Medium",diff:1,count:20},{label:"Hard",diff:2,count:15}],
    topics:["lists","strings","primitives","objects","arrays","recursion","search-sort","arrays2d"], seedBase:601 },
  // C++: expanded to 100+ sets
  { lang:"cpp",    bands:[{label:"Essentials",diff:0,count:20}], topics:["arrays","primitives","strings","objects","search-sort"], seedBase:1301 },
  { lang:"cpp",    bands:[{label:"Foundations",diff:0,count:7},{label:"Intermediate",diff:1,count:8},{label:"Advanced",diff:2,count:5}],
    topics:["arrays","objects","strings","search-sort","arrays2d","primitives","inheritance","polymorphism","recursion","arrays"], seedBase:303 },
  { lang:"cpp",    bands:[{label:"Easy",diff:0,count:15},{label:"Medium",diff:1,count:15},{label:"Hard",diff:2,count:10}],
    topics:["arrays","primitives","strings","objects","search-sort","arrays2d","recursion","inheritance"], seedBase:701 },
  // C: expanded to 65+ sets
  { lang:"c",      bands:[{label:"Essentials",diff:0,count:15}], topics:["primitives","arrays","strings","recursion","search-sort"], seedBase:1401 },
  { lang:"c",      bands:[{label:"Foundations",diff:0,count:4},{label:"Intermediate",diff:1,count:4},{label:"Advanced",diff:2,count:2}],
    topics:["arrays","primitives","strings","recursion","search-sort","arrays","primitives","strings","arrays","recursion"], seedBase:404 },
  { lang:"c",      bands:[{label:"Easy",diff:0,count:10},{label:"Medium",diff:1,count:10},{label:"Hard",diff:2,count:8}],
    topics:["primitives","arrays","strings","recursion","search-sort","arrays2d"], seedBase:801 },
  // JavaScript: 80+ sets
  { lang:"javascript", bands:[{label:"Essentials",diff:0,count:15}], topics:["primitives","arrays","strings","objects","functions"], seedBase:1501 },
  { lang:"javascript", bands:[{label:"Foundations",diff:0,count:10},{label:"Intermediate",diff:1,count:10},{label:"Advanced",diff:2,count:8}],
    topics:["primitives","arrays","strings","objects","functions","search-sort","recursion","arrays2d"], seedBase:901 },
  { lang:"javascript", bands:[{label:"Easy",diff:0,count:12},{label:"Medium",diff:1,count:12},{label:"Hard",diff:2,count:10}],
    topics:["arrays","strings","objects","functions","recursion","search-sort"], seedBase:1001 },
  // Cybersecurity: 30+ sets
  { lang:"cybersecurity", bands:[{label:"Fundamentals",diff:0,count:15},{label:"Intermediate",diff:1,count:15}],
    topics:["networks","encryption","vulnerabilities","best-practices"], seedBase:1601 },
  // Web Dev: 30+ sets
  { lang:"webdev", bands:[{label:"Fundamentals",diff:0,count:15},{label:"Intermediate",diff:1,count:15}],
    topics:["html","css","javascript"], seedBase:1701 },
  // Math: 20+ sets
  { lang:"math", bands:[{label:"Fundamentals",diff:0,count:10},{label:"Intermediate",diff:1,count:10}],
    topics:["logic","sets","probability","combinatorics","algebra"], seedBase:1801 },
];

fs.rmSync(SETS_DIR, { recursive: true, force: true });
const bundle = { version: "1.0", exportedAt: Date.now(), quizzes: [], adminSettings: {} };
let totalQ = 0;
const langCounters = {};
for (const plan of PLAN) {
  const dir = path.join(SETS_DIR, plan.lang);
  fs.mkdirSync(dir, { recursive: true });
  langCounters[plan.lang] = langCounters[plan.lang] || 0;
  const quizzes = assembleLanguage(plan.lang, plan, langCounters[plan.lang]);
  langCounters[plan.lang] += quizzes.length;
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
