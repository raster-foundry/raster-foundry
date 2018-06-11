# Anti-meridian artifacts

## Observation
Polygons which cross the anti-meridian are ambiguous. 
There are various heuristics to determine what the best to display them is, 
but none of them are correct in all cases.  
Fixing a polygon has multiple solutions and they're all relatively easy to 
implement, but determining which polygons need to be fixed is a less straightforward question.

## Possible solutions
None of these address every possible case since it is a mathematically ambiguous problem.
Each heuristic makes a compromise somewhere.

### Smallest area
Calculate the area of the polygon two ways - one in which the polygon crosses the anti-meridian, 
and the other in which it wraps around in the other direction. Assume that the smaller one is correct.

#### Advantages
Relatively simple to implement, and most likely to be correct for all imagery

#### Disadvantages
Any polygons which cover more than half of the world horizontally will be shown as wrapping around the world in the wrong direction.
Most imagery that we handle does not cover such a wide area, as imagery providers tend to chop up their imagery for easier processing.

### Degree difference threshold
calculation: Abs(westmost coord + eastmost coord) < degree threshold

#### Advantages
- Only affects polygons which are close to the meridian
 
#### Disadvantages
- Doesn't fix polygons which are outside the threshold.
- Can have false positives on small polygons which are close to the anti-meridian
- threshold must be tuned to the expected size of the polygons - can't deal with large and small polygons equally well

### Assume that no polygons cross the anti-meridian

#### Advantages
- Easy
- assume that all polygon sources will split into multipolygons instead of crossing the anti-meridian
#### Disadvantages
Doesn't actually address the problem since we can't choose where our polygons are coming from

### Combine Smallest area and Degree difference threshold
Also only apply to thumbnails
#### Advantages
- Is somewhat better that each of the solutions individually
- Avoids issue with small polygons near the anti-meridian

#### Disadvantages
- Still has issues with very large polygons, but now both the eastmost and westmost points must be relatively near the anti-meridian.

## Proposal
Go with the hybrid smallest area and degree difference approach for now, and only apply it to scene footprints. We don't currently support any imagery sources that cover wide swaths of the globe, so this should be good enough as a temporary solution.
