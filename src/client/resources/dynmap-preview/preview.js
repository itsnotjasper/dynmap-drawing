(function () {
    'use strict';

    const statusEl = document.getElementById('status');
    const liveLinkEl = document.getElementById('live-dynmap-link');

    function setStatus(msg, isError) {
        statusEl.textContent = msg;
        statusEl.className = isError ? 'error' : '';
    }

    function parseExport(json) {
        if (!json) {
            return [];
        }
        if (json.sets && typeof json.sets === 'object') {
            const lines = [];
            const setIds = Object.keys(json.sets);
            for (let si = 0; si < setIds.length; si++) {
                const setNode = json.sets[setIds[si]];
                if (!setNode || !setNode.lines) {
                    continue;
                }
                const lineIds = Object.keys(setNode.lines);
                for (let li = 0; li < lineIds.length; li++) {
                    const line = setNode.lines[lineIds[li]];
                    if (line && Array.isArray(line.x) && line.x.length >= 2) {
                        lines.push(line);
                    }
                }
            }
            return lines;
        }
        if (Array.isArray(json.submissions)) {
            return json.submissions
                .map(function (s) { return s.line; })
                .filter(function (line) {
                    return line && Array.isArray(line.x) && line.x.length >= 2;
                });
        }
        if (json.x && json.y && json.z) {
            return [json];
        }
        return [];
    }

    function resolveWorldMap(config) {
        return {
            worldName: config.defaultworld || 'new',
            mapName: config.defaultmap || 'flat',
        };
    }

    function findMapDefinition(config, worldName, mapName) {
        const worlds = config.worlds || [];
        for (let i = 0; i < worlds.length; i++) {
            if (worlds[i].name !== worldName) {
                continue;
            }
            const maps = worlds[i].maps || [];
            for (let j = 0; j < maps.length; j++) {
                if (maps[j].name === mapName) {
                    return maps[j];
                }
            }
        }
        return null;
    }

    function createDynmapProjection(mapDef) {
        const tileSize = 128 << (mapDef.tilescale || 0);
        const nativeZoomLevels = mapDef.mapzoomout || 1;
        const mapScale = mapDef.scale || 1;
        const divisor = (1 << nativeZoomLevels) / mapScale;
        const wtp = mapDef.worldtomap || [0, 0, 0, 0, 0, 0, 0, 0, 0];
        return {
            locationToLatLng: function (loc) {
                const mapY = wtp[3] * loc.x + wtp[4] * loc.y + wtp[5] * loc.z;
                const mapX = wtp[0] * loc.x + wtp[1] * loc.y + wtp[2] * loc.z;
                return L.latLng(
                    -((tileSize - mapY) / divisor),
                    mapX / divisor
                );
            },
        };
    }

    function projectLine(line, projection) {
        const latlngs = [];
        for (let i = 0; i < line.x.length; i++) {
            const ll = projection.locationToLatLng({
                x: line.x[i],
                y: line.y[i],
                z: line.z[i],
            });
            if (Number.isFinite(ll.lat) && Number.isFinite(ll.lng)) {
                latlngs.push(ll);
            }
        }
        return latlngs;
    }

    function lineMidpointWorld(line) {
        const mid = Math.floor(line.x.length / 2);
        return {
            x: Math.round(line.x[mid]),
            y: Math.round(line.y[mid]),
            z: Math.round(line.z[mid]),
        };
    }

    function buildLiveDynmapUrl(baseUrl, worldName, mapName, line) {
        const mid = lineMidpointWorld(line);
        const hash = encodeURIComponent(worldName) + ';'
            + encodeURIComponent(mapName) + ';'
            + mid.x + ',' + mid.y + ',' + mid.z + ';4';
        return baseUrl.replace(/#$/, '') + '#' + hash;
    }

    function zoomPrefix(level) {
        if (level === 0) {
            return '';
        }
        return 'z'.repeat(level) + '_';
    }

    function buildHdTileName(coords, options) {
        const zoomForUrl = options.maxNativeZoom - coords.z;
        const nativeZoom = Math.max(0, zoomForUrl - (options.extraZoomLevels || 0));
        const scale = 1 << nativeZoom;
        let x = scale * coords.x;
        let y = -(scale * coords.y);
        const scaledx = x >> 5;
        const scaledy = y >> 5;
        const nightday = options.nightAndDay ? '_day' : '';
        return options.prefix + nightday + '/'
            + scaledx + '_' + scaledy + '/'
            + zoomPrefix(nativeZoom) + x + '_' + y + '.'
            + options.imageFormat;
    }

    function createDynmapTileLayer(baseUrl, worldName, mapName, mapDef) {
        const tileSize = 128 << (mapDef.tilescale || 0);
        const nativeZoomLevels = mapDef.mapzoomout || 1;
        const extraZoomLevels = mapDef.mapzoomin || 0;
        const prefix = mapDef.prefix || mapName;
        const imageFormat = mapDef['image-format'] || 'png';
        const tileBaseUrl = baseUrl + 'tiles/' + worldName + '/';
        const layerOptions = {
            prefix: prefix,
            imageFormat: imageFormat,
            nightAndDay: !!mapDef.nightandday,
            extraZoomLevels: extraZoomLevels,
            zoomReverse: true,
            minZoom: 0,
            maxZoom: nativeZoomLevels + extraZoomLevels,
            maxNativeZoom: nativeZoomLevels,
            tileSize: L.point(tileSize, tileSize),
            noWrap: true,
            attribution: 'Dynmap tiles',
        };
        const layer = L.tileLayer(tileBaseUrl, layerOptions);
        layer.getTileUrl = function (coords) {
            return tileBaseUrl + buildHdTileName(coords, layerOptions);
        };
        return layer;
    }

    function renderPreview(config, previewData, dynmapConfig) {
        const lines = parseExport(previewData);
        if (!lines.length) {
            setStatus('No preview lines in payload.', true);
            return;
        }

        const resolved = resolveWorldMap(dynmapConfig);
        const mapDef = findMapDefinition(dynmapConfig, resolved.worldName, resolved.mapName);
        if (!mapDef) {
            setStatus('Map not found: ' + resolved.worldName + '/' + resolved.mapName, true);
            return;
        }

        const projection = createDynmapProjection(mapDef);
        const map = L.map('map', {
            crs: L.CRS.Simple,
            preferCanvas: true,
            zoomControl: true,
            attributionControl: true,
        });

        createDynmapTileLayer(config.dynmapBaseUrl, resolved.worldName, resolved.mapName, mapDef).addTo(map);

        const group = L.featureGroup();
        let totalDrawn = 0;

        for (let li = 0; li < lines.length; li++) {
            const line = lines[li];
            const latlngs = projectLine(line, projection);
            if (latlngs.length < 2) {
                continue;
            }
            const poly = L.polyline(latlngs, {
                color: line.color || '#00FF00',
                weight: line.weight || 3,
                opacity: line.opacity != null ? line.opacity : 0.85,
            });
            if (line.label) {
                poly.bindPopup(line.label);
            }
            group.addLayer(poly);
            totalDrawn++;
        }

        if (!totalDrawn) {
            setStatus('No preview points after projection.', true);
            return;
        }

        group.addTo(map);
        map.fitBounds(group.getBounds(), { padding: [40, 40], maxZoom: 6 });

        const firstLine = lines[0];
        liveLinkEl.href = buildLiveDynmapUrl(config.dynmapBaseUrl, resolved.worldName, resolved.mapName, firstLine);
        liveLinkEl.hidden = false;

        setStatus('Rendered ' + totalDrawn + ' draft line(s) on ' + resolved.worldName + '/' + resolved.mapName + '.');
    }

    function fetchJson(url) {
        return fetch(url, { credentials: 'omit' }).then(function (resp) {
            if (!resp.ok) {
                throw new Error('HTTP ' + resp.status + ' for ' + url);
            }
            return resp.json();
        });
    }

    Promise.all([
        fetchJson('/preview/config.json'),
        fetchJson('/preview.json'),
    ])
        .then(function (results) {
            const config = results[0];
            const previewData = results[1];
            const dynmapConfigUrl = new URL('standalone/dynmap_config.json?_=' + Date.now(), config.dynmapBaseUrl).href;
            return fetchJson(dynmapConfigUrl).then(function (dynmapConfig) {
                renderPreview(config, previewData, dynmapConfig);
            });
        })
        .catch(function (err) {
            console.error('[MRT Preview]', err);
            setStatus('Preview failed: ' + err.message, true);
        });
})();
