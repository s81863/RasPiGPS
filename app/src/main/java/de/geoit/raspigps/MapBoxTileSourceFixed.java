package de.geoit.raspigps;

import org.osmdroid.tileprovider.tilesource.MapBoxTileSource;
import org.osmdroid.util.MapTileIndex;

public class MapBoxTileSourceFixed extends MapBoxTileSource {
    MapBoxTileSourceFixed(String name, int zoomMinLevel, int zoomMaxLevel, int tileSizePixels) {
        super(name, zoomMinLevel, zoomMaxLevel, tileSizePixels, "");
    }

    @Override public String getTileURLString(final long pMapTileIndex) {
        StringBuilder url = new StringBuilder("https://api.mapbox.com/styles/v1/mapbox/");
        url.append(getMapBoxMapId());
        url.append("/tiles/");
        url.append(MapTileIndex.getZoom(pMapTileIndex));
        url.append("/");
        url.append(MapTileIndex.getX(pMapTileIndex));
        url.append("/");
        url.append(MapTileIndex.getY(pMapTileIndex));
        //url.append("@2x"); //for high-res
        url.append("?access_token=").append(getAccessToken());
        String res = url.toString();
        return res;
    }
}
