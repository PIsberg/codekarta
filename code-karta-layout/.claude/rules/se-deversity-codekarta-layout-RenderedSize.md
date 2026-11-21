---
paths: ["**/RenderedSize.java"]
---

<!-- VIBETAGS-START -->
# Rules for RenderedSize

### Rules for method estimateHeight
- **Rule**: Free to change, but every mirror must change in the same commit.
- **Mirrors**: se.deversity.codekarta.render.SvgRenderer#computeCompartmentHeight
- **Reason**: Layout may not reference render, so the compartment-height formula exists twice. If SvgRenderer draws compartments taller than this estimate, both layout engines leave too little room and class boxes overlap; if shorter, diagrams grow gaps.
- **Enforced by**: RenderedOverlapTest.theEstimateMatchesWhatSvgRendererDraws, against heights measured from SvgRenderer output; no test compares the two formulas directly
<!-- VIBETAGS-END -->
