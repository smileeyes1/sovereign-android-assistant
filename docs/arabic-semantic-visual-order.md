# Arabic-first semantic and visual order (ar-PS)
Version: 2026-10-10-v1. This policy augments the existing HakimArabicPolicy; it does **not** replace user governance or authorize modifying or deploying Android without review.

## The failure this contract prevents
In a grade-2 Arabic story, **the first basket (٢٣) must be visually to the right of the second (١٤)**. `dir=rtl` or `text-align:right` alone does not guarantee that an SVG, canvas, CSS grid, image, gallery, voice narration, DOM focus order, and accessible reading order all agree.

## Required invariants
1. Default language ar-PS when appropriate, clear Arabic punctuation and spacing, RTL paragraph start and initial alignment to the right.
2. In a horizontal **semantic Arabic sequence**, item one is on the right of item two; DOM order, keyboard focus, screen-reader sequence, voice and corresponding visual order must agree. Exceptions require explicit meaning, not an automatic mirror.
3. In the two-basket reference case, visually confirm centerX(first: ٢٣) > centerX(second: ١٤); narration says first then second.
4. Positional mathematics is independent of prose RTL: in a two-place number, tens are on the left, ones on the right. To add two two-place numbers without carrying, calculate ones first and tens second. A right-to-left Arabic math row is visually operand1, plus, operand2, equals, answer (right to left). Inspect actual rendered symbols rather than reversing a text string.
5. Student-facing grade 1/2 digits are ٠١٢٣٤٥٦٧٨٩. Each bundle of ten depicts ten actual units. Do not confuse decoration with countable objects.
6. Respect mathematical/physical exceptions: ascending number lines may increase to the right as in the textbook; charts, maps, paths, code snippets, URLs, identifiers and email addresses must keep their natural semantics. Use explicit directional isolation as needed, not a global transform.
7. Any HTML, Android UI, PDF/Word, animation, cinema overlay or narrated interface must be checked on the final output: text shaping, positions, order, symbols, clipping, overlap, actions, accessibility and fonts.
8. Before the lesson, do not test the child on an untaught skill; reserve mastery measurement for after instruction and independent practice.

## Regression scenarios
- AR-VIS-01: ar-PS horizontal baskets: first ٢٣ (right), second ١٤ (left) and matching audible/accessible order.
- AR-VIS-02: place value of ٢٣: tens=٢ (left), ones=٣ (right).
- AR-VIS-03: math row ٢٣ + ١٤ = □ has first operand on right and box on left; result ٣٧; ones calculation precedes tens.
- AR-VIS-04: a pack labelled ten shows ten countable marks.
- AR-VIS-05: standard number line orientation follows the book, not Arabic paragraph direction.
- AR-VIS-06: Arabic text/captions/control labels/screen reader/focus/print rendered at phone widths 320, 360, 390, 430, tablet, and print if applicable.
- AR-VIS-07: technical IDs/URLs remain readable; no unintentional digit substitution.

## Gate
Policy-string tests in `tests/verify_ar_ps_language.py` and `tests/verify_unified_hakim.py` check that the requirement is not silently dropped from Hakim's prompt/defaults. **They are not visual UI tests.** Each affected actual screen or generated artifact still needs screenshot or direct visual testing, plus accessible order and regression checks. CI success does not prove installation on the phone, suitability for classroom, or changes to unrelated ChatGPT projects.

## Deployment
Keep changes on a review branch. Require independent review and the appropriate build/UI tests before merge. Do not infer rights to alter ChatGPT account settings, past conversations, other project instructions, or installed APKs from this repository policy.
