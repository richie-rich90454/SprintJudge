# Code-execution audit — 500 sampled questions

**Method.** For each of 10 subjects, 50 code-format questions were sampled from `src/main/resources/bank/<subject>/`, code was extracted from the stem, and executed in `executor/tmp/verify-audit/run/` using javac/java, python3, gcc, g++ and node. stdout was compared with the keyed correct option. Bank files were not edited.

**Executed coverage (of 50 each):** c 50, javascript 39, java 35, data-structures 26, python 2, computing-foundations 2, cpp 0, web-development/operating-systems/cybersecurity 0. Non-executed stems were CSS prose, one-line pseudo-code, or contained no output statement.

## Ranked defects

1. **Wrong keyed answer (high)** — `java-programming/java-programming-u1-t3-primitive-types.json`, `jp-1.3-0033`: stem `System.out.println(7 % 3 + 2);`; key says "The console output is 4"; javac/java prints **3**. The explanation is generic boilerplate and does not match actual output.
2. **Wrong keyed answer (high)** — `java-programming/java-programming-u1-t8-character-literals.json`, `jp-1.8-0023`: `'Z' - 'A'` keyed 26; actual **25**. Same boilerplate explanation.
3. **Non-runnable code stem (medium)** — `java-programming/java-programming-u1-t9-primitive-types.json`, `jp-1.9-0052`: stem uses `print s;`, which is not Java; javac fails. Explanation assumes `System.out.print(s)`.
4. **Malformed option text (low)** — same file, `jp-1.9-0015`: correct option text is "The choice B" (others "The choice A1"); explanation computes 'B', so the option is a placeholder rather than an answer.
5. **Systemic invalid pseudo-Python (medium)** — `python/python-u1-t10-break-leaves-a-for-loop.json`, `py-1.10-0001` et al.: stems such as `for x in [1,2,3,4]: if x == 3: break; print(x)` raise SyntaxError; keyed outputs depend on an unstated indentation convention.

All executed C, JavaScript, data-structures and computing-foundations samples matched their keys.
