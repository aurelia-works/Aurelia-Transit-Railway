/**
 * Splits a route shape into runs along the rails and straight runs (where the server found no rails between two platforms).
 * Neighbouring runs share their end point so the drawn line has no gaps.
 *
 * @param shape {number[][]} points [x, y, z]
 * @param straight {number[][]} [first, last] point index ranges of straight runs, in order
 * @returns {{straight: boolean, points: number[][]}[]}
 */
export function splitShape(shape, straight) {
    const runs = [];
    let next = 0;
    (straight || []).forEach(([first, last]) => {
        if (first > next) runs.push({straight: false, points: shape.slice(next, first + 1)});
        runs.push({straight: true, points: shape.slice(first, last + 1)});
        next = last;
    });
    if (next < shape.length - 1 || runs.length === 0) runs.push({straight: false, points: shape.slice(next)});
    return runs;
}
