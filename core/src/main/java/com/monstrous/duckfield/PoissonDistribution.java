package com.monstrous.duckfield;

// poisson disk distribution
// generate an array of points randomly distributed in the given rectangle but no pair closer together than a minimum distance.
//
//
// Uses Bridson's algorithm
// See also: https://sighack.com/post/poisson-disk-sampling-bridsons-algorithm
//

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class PoissonDistribution {

    public static final int MAX_TRIES = 20;

    public Array<Vector2> generatePoissonDistribution(float minDistance, Rectangle area) {


        Array<Vector2> output = new Array<>();
        Array<Vector2> active = new Array<>();

        float cellSize = minDistance / (float)Math.sqrt(2f);
        int w = (int) Math.ceil(area.width / cellSize);
        int h = (int) Math.ceil(area.height / cellSize);
        Vector2[][] grid = new Vector2[w][h];	// initialized to nulls


        Vector2 p0 = new Vector2( MathUtils.random(area.x, area.x+area.width), MathUtils.random(area.y, area.y+area.height) );

        output.add(p0);
        active.add(p0);
        addToGrid(grid, cellSize, area, p0);

        while(active.size > 0 ) {

            // get a random point from the active list
            int randomIndex = MathUtils.random(active.size-1);	// random index in [0 .. size-1]
            Vector2 p = active.get(randomIndex);

            boolean found = false;
            for(int attempt = 0; attempt < MAX_TRIES; attempt++) {	// try k times before giving up

                // generate a random point between r and 2r distance from p
                float distance = MathUtils.random(minDistance, 2f*minDistance);
                float angle = MathUtils.random(0, 2f*MathUtils.PI);

                float x = p.x + distance*(float) Math.cos(angle);
                float y = p.y + distance*(float) Math.sin(angle);
                Vector2 pnew = new Vector2(x, y);

                if(isValidPoint(grid, cellSize, area, pnew, minDistance)){
                    output.add(pnew);
                    active.add(pnew);
                    addToGrid(grid, cellSize, area, pnew);
                    found = true;
                    break;
                }
            }
            if(!found)
                active.removeIndex(randomIndex);		// remove p from active list
        }
        return output;

    }


    private void addToGrid(Vector2[][] grid, float cellSize, Rectangle area, Vector2 p) {
        int x = (int)Math.floor((p.x - area.x)/ cellSize);
        int y = (int)Math.floor((p.y - area.y)/ cellSize);
        grid[x][y] = p;
    }


    private boolean isValidPoint(Vector2[][] grid, float cellSize, Rectangle area, Vector2 p, float minDistance) {
        if(!area.contains(p))
            return false;

        int xp = (int)Math.floor((p.x - area.x)/ cellSize);
        int yp = (int)Math.floor((p.y - area.y)/ cellSize);
        // check the cell and the 8 neighbouring cells

        for(int dx = -1; dx <= 1; dx++){
            for(int dy = -1; dy <= 1; dy++) {
                int x = xp + dx;
                int y = yp + dy;
                if(x < 0 || y < 0)
                    continue;
                if(x >= grid.length || y >= grid[0].length )
                    continue;
                if(grid[x][y] != null) {
                    float distance = p.dst(grid[x][y]);
                    if(distance < minDistance)
                        return false;
                }
            }
        }
        return true;
    }
}
