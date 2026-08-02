(function () {

    if (window.__MRT_DYNMAP_PREVIEW__) {
        return;
    }

    (function installMapCaptureHook() {
        if (window.__MRT_MAP_CAPTURE_INSTALLED__) {
            return;
        }
        window.__MRT_MAP_CAPTURE_INSTALLED__ = true;
        window.__MRT_CAPTURED_LEAFLET_MAP__ = window.__MRT_CAPTURED_LEAFLET_MAP__ || null;
        window.__MRT_CAPTURED_HAS_LAYER_MANAGER__ = !!(
            window.__MRT_CAPTURED_LEAFLET_MAP__ &&
            typeof window.__MRT_CAPTURED_LEAFLET_MAP__.getLayerManager === 'function'
        );

        function captureMapCandidate(result) {
            if (!result || typeof result.getContainer !== 'function') {
                return;
            }
            if (typeof result.getLayerManager === 'function') {
                window.__MRT_CAPTURED_LEAFLET_MAP__ = result;
                window.__MRT_CAPTURED_HAS_LAYER_MANAGER__ = true;
                return;
            }
            if (
                !window.__MRT_CAPTURED_HAS_LAYER_MANAGER__ &&
                typeof result.latLngToLayerPoint === 'function' &&
                typeof result.getPane === 'function'
            ) {
                window.__MRT_CAPTURED_LEAFLET_MAP__ = result;
            }
        }

        function patchMapInitialize(proto) {
            if (!proto || proto.__mrtInitPatched || typeof proto.initialize !== 'function') {
                return;
            }
            const origInit = proto.initialize;
            proto.initialize = function () {
                const out = origInit.apply(this, arguments);
                captureMapCandidate(this);
                return out;
            };
            proto.__mrtInitPatched = true;
        }

        function patchMapAddLayer(proto) {
            if (!proto || proto.__mrtAddLayerPatched || typeof proto.addLayer !== 'function') {
                return;
            }
            if (typeof proto.latLngToLayerPoint !== 'function' && typeof proto.getContainer !== 'function') {
                return;
            }
            const origAddLayer = proto.addLayer;
            proto.addLayer = function () {
                captureMapCandidate(this);
                return origAddLayer.apply(this, arguments);
            };
            proto.__mrtAddLayerPatched = true;
        }

        function patchMapPrototype(proto) {
            patchMapInitialize(proto);
            patchMapAddLayer(proto);
        }

        const origConstruct = Reflect.construct;
        Reflect.construct = function (target, args, newTarget) {
            const result = origConstruct.apply(this, arguments);
            try {
                captureMapCandidate(result);
                if (target && target.prototype) {
                    patchMapPrototype(target.prototype);
                }
                if (result) {
                    let proto = Object.getPrototypeOf(result);
                    while (proto) {
                        patchMapPrototype(proto);
                        proto = Object.getPrototypeOf(proto);
                    }
                }
            } catch (e) {
                // ignore
            }
            return result;
        };
    })();

    const PREVIEW_LAYER_NAME = 'MRT draft preview';
    let previewLayer = null;
    let cachedDynmapConfig = null;
    let pendingData = null;
    let lastPreviewData = null;
    let waitTimer = null;
    let mapLayerHooked = false;
    let waitAttempts = 0;

    function log(msg) {
        console.info('[MRT Dynmap Preview]', msg);
    }

    function debugLog(hypothesisId, location, message, data) {
        console.info('[MRT Dynmap Preview][debug]', hypothesisId, location, message, data);
        // #region agent log
        fetch('http://127.0.0.1:7697/ingest/277df6f0-d79f-45ac-9b2a-16fd06847301', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Debug-Session-Id': 'db0844' },
            body: JSON.stringify({
                sessionId: 'db0844',
                hypothesisId: hypothesisId,
                location: location,
                message: message,
                data: data,
                timestamp: Date.now(),
            }),
        }).catch(function () {});
        // #endregion
    }

    let lastFindMethod = null;

    function isLeafletMap(candidate) {
        if (!candidate) {
            return false;
        }
        if (candidate.__mrtDomAdapter) {
            return true;
        }
        return !!(
            typeof candidate.latLngToLayerPoint === 'function' &&
            typeof candidate.getPane === 'function' &&
            typeof candidate.getContainer === 'function'
        );
    }

    function getCapturedLeafletMap(container) {
        const captured = window.__MRT_CAPTURED_LEAFLET_MAP__;
        if (!isLeafletMap(captured)) {
            return null;
        }
        if (container && typeof captured.getContainer === 'function') {
            try {
                if (captured.getContainer() !== container) {
                    return null;
                }
            } catch (e) {
                return null;
            }
        }
        return captured;
    }

    function parseCssTranslate(transform) {
        if (!transform || transform === 'none') {
            return { x: 0, y: 0, scale: 1 };
        }
        if (transform.indexOf('matrix3d(') === 0) {
            const parts = transform.slice(9, -1).split(',').map(Number);
            return { x: parts[12] || 0, y: parts[13] || 0, scale: parts[0] || 1 };
        }
        if (transform.indexOf('matrix(') === 0) {
            const parts = transform.slice(7, -1).split(',').map(Number);
            return { x: parts[4] || 0, y: parts[5] || 0, scale: parts[0] || 1 };
        }
        return { x: 0, y: 0, scale: 1 };
    }

    function readLiveAtlasZoom(container) {
        const hash = (window.location.hash || '').replace(/^#/, '');
        const parts = hash.split(';').filter(function (part) {
            return part && part.indexOf('we_dl_preview=') !== 0;
        });
        if (parts.length >= 6) {
            const zoom = parseInt(parts[5], 10);
            if (Number.isFinite(zoom)) {
                return zoom;
            }
        }
        const tilePane = container && container.querySelector('.leaflet-tile-pane');
        if (tilePane) {
            const scale = parseCssTranslate(getComputedStyle(tilePane).transform).scale;
            if (scale > 0 && Number.isFinite(scale)) {
                return Math.max(0, Math.round(Math.log(scale) / Math.LN2));
            }
        }
        return 0;
    }

    function createDomMapAdapter(container) {
        const listeners = {};
        let domHooked = false;

        function getMapPane() {
            return container.querySelector('.leaflet-map-pane');
        }

        function readMapPanePos() {
            const pane = getMapPane();
            if (!pane) {
                return { x: 0, y: 0 };
            }
            const parsed = parseCssTranslate(getComputedStyle(pane).transform);
            return { x: parsed.x, y: parsed.y };
        }

        function getSize() {
            return { x: container.clientWidth, y: container.clientHeight };
        }

        function latLngToLayerPoint(latlng) {
            const zoom = readLiveAtlasZoom(container);
            const scale = Math.pow(2, zoom);
            const projected = { x: latlng.lng * scale, y: latlng.lat * scale };
            const size = getSize();
            const panePos = readMapPanePos();
            const pixelOrigin = {
                x: -panePos.x + size.x / 2,
                y: -panePos.y + size.y / 2,
            };
            return {
                x: projected.x - pixelOrigin.x + size.x / 2,
                y: projected.y - pixelOrigin.y + size.y / 2,
            };
        }

        function containerPointToLayerPoint(point) {
            const panePos = readMapPanePos();
            return { x: point[0] - panePos.x, y: point[1] - panePos.y };
        }

        function fireEvents() {
            const names = ['move', 'zoom', 'resize', 'viewreset'];
            for (let i = 0; i < names.length; i++) {
                const list = listeners[names[i]];
                if (!list) {
                    continue;
                }
                for (let j = 0; j < list.length; j++) {
                    try {
                        list[j]();
                    } catch (e) {
                        // ignore
                    }
                }
            }
        }

        function hookDomEvents() {
            if (domHooked) {
                return;
            }
            domHooked = true;
            const pane = getMapPane();
            if (pane) {
                const observer = new MutationObserver(fireEvents);
                observer.observe(pane, { attributes: true, attributeFilter: ['style'] });
            }
            window.addEventListener('resize', fireEvents);
            window.addEventListener('hashchange', fireEvents);
        }

        return {
            __mrtDomAdapter: true,
            _layers: {},
            getContainer: function () {
                return container;
            },
            getZoom: function () {
                return readLiveAtlasZoom(container);
            },
            getPane: function (name) {
                return container.querySelector('.leaflet-' + name + '-pane') || getMapPane();
            },
            getSize: getSize,
            latLngToLayerPoint: latLngToLayerPoint,
            containerPointToLayerPoint: containerPointToLayerPoint,
            latLngToContainerPoint: function (latlng) {
                const layerPoint = latLngToLayerPoint(latlng);
                const panePos = readMapPanePos();
                return { x: layerPoint.x + panePos.x, y: layerPoint.y + panePos.y };
            },
            fitBounds: function () {
                // DOM adapter cannot pan the LiveAtlas map programmatically.
            },
            on: function (eventName, fn) {
                if (!listeners[eventName]) {
                    listeners[eventName] = [];
                }
                listeners[eventName].push(fn);
                hookDomEvents();
            },
        };
    }

    function extractLeafletFromVueComponent(vueComponent) {
        if (!vueComponent) {
            return null;
        }
        const candidates = [
            vueComponent.leaflet,
            vueComponent.proxy && vueComponent.proxy.leaflet,
            vueComponent.ctx && vueComponent.ctx.leaflet,
            vueComponent.props && vueComponent.props.leaflet,
            vueComponent.setupState && vueComponent.setupState.leaflet,
            vueComponent.exposed && vueComponent.exposed.leaflet,
            vueComponent.data && vueComponent.data.leaflet,
        ];
        for (let i = 0; i < candidates.length; i++) {
            if (isLeafletMap(candidates[i])) {
                return candidates[i];
            }
        }
        return null;
    }

    function walkVueParentChain(vueComponent, container) {
        let cur = vueComponent;
        while (cur) {
            const candidate = extractLeafletFromVueComponent(cur);
            if (candidate && mapMatchesContainer(candidate, container)) {
                return candidate;
            }
            cur = cur.parent;
        }
        return null;
    }

    function findMapFromElementVueContext(el, container) {
        if (!el) {
            return null;
        }
        const vue = el.__vueParentComponent;
        if (vue) {
            const fromChain = walkVueParentChain(vue, container);
            if (fromChain) {
                return fromChain;
            }
        }
        const vnode = el.__vnode;
        if (vnode && vnode.component) {
            const fromVnode = walkVueParentChain(vnode.component, container);
            if (fromVnode) {
                return fromVnode;
            }
        }
        return null;
    }

    function findMapViaMapSibling(container) {
        if (!container) {
            return null;
        }
        let sibling = container.previousElementSibling;
        while (sibling) {
            const fromSibling = findMapFromElementVueContext(sibling, container);
            if (fromSibling) {
                return fromSibling;
            }
            sibling = sibling.previousElementSibling;
        }
        const mapDiv = document.querySelector('.map');
        if (mapDiv && mapDiv !== container) {
            return findMapFromElementVueContext(mapDiv, container);
        }
        return null;
    }

    function findMapViaVueParentChainScan(container) {
        const roots = [document.getElementById('app'), document.body];
        for (let r = 0; r < roots.length; r++) {
            const root = roots[r];
            if (!root) {
                continue;
            }
            const nodes = root.getElementsByTagName('*');
            for (let i = 0; i < nodes.length; i++) {
                const vue = nodes[i].__vueParentComponent;
                if (!vue) {
                    continue;
                }
                const fromChain = walkVueParentChain(vue, container);
                if (fromChain) {
                    return fromChain;
                }
            }
        }
        return null;
    }

    function isResolvableMap(obj, container) {
        return isLeafletMap(obj) && mapMatchesContainer(obj, container);
    }

    function isLiveAtlasMap(obj, container) {
        return isResolvableMap(obj, container) &&
            typeof obj.getLayerManager === 'function';
    }

    function findMapViaDomLayerScan(container) {
        if (!container) {
            return null;
        }
        const selectors = [
            '.leaflet-marker-icon',
            '.leaflet-interactive',
            '.leaflet-zoom-animated',
            '.leaflet-tile',
            '.leaflet-control-loading',
            '.leaflet-pane canvas',
            '.leaflet-pane svg',
        ];
        for (let si = 0; si < selectors.length; si++) {
            const nodes = container.querySelectorAll(selectors[si]);
            for (let ni = 0; ni < nodes.length; ni++) {
                const el = nodes[ni];
                for (const key in el) {
                    try {
                        const val = el[key];
                        if (val && val._map && isResolvableMap(val._map, container)) {
                            return val._map;
                        }
                    } catch (e) {
                        // ignore
                    }
                }
            }
        }
        return null;
    }

    function findMapViaDeepObjectScan(container) {
        const roots = [];
        const appEl = document.getElementById('app');
        if (appEl && appEl.__vue_app__) {
            roots.push(appEl.__vue_app__);
        }
        const sibling = container && container.previousElementSibling;
        if (sibling) {
            roots.push(sibling);
        }
        const seen = new WeakSet();
        function scan(obj, depth) {
            if (!obj || typeof obj !== 'object' || depth > 32) {
                return null;
            }
            if (seen.has(obj)) {
                return null;
            }
            seen.add(obj);
            try {
                if (obj._map && isResolvableMap(obj._map, container)) {
                    return obj._map;
                }
                if (isResolvableMap(obj, container)) {
                    return obj;
                }
                if (obj.leaflet && isResolvableMap(obj.leaflet, container)) {
                    return obj.leaflet;
                }
            } catch (e) {
                // ignore
            }
            if (obj instanceof Element || obj instanceof Window || obj instanceof Document) {
                return null;
            }
            let keys;
            try {
                keys = Object.keys(obj);
            } catch (e) {
                return null;
            }
            for (let ki = 0; ki < keys.length && ki < 80; ki++) {
                try {
                    const child = obj[keys[ki]];
                    if (child && typeof child === 'object') {
                        const found = scan(child, depth + 1);
                        if (found) {
                            return found;
                        }
                    }
                } catch (e) {
                    // ignore
                }
            }
            return null;
        }
        for (let ri = 0; ri < roots.length; ri++) {
            const found = scan(roots[ri], 0);
            if (found) {
                return found;
            }
        }
        return null;
    }

    function findMapViaDeepVueScan(container) {
        return findMapViaDeepObjectScan(container);
    }

    function findMapViaSiblingVnode(container) {
        const sibling = container && container.previousElementSibling;
        if (!sibling) {
            return null;
        }
        const vnode = sibling.__vnode;
        if (!vnode || !vnode.component) {
            return null;
        }
        const fromComponent = extractLeafletFromVueComponent(vnode.component);
        if (fromComponent && mapMatchesContainer(fromComponent, container)) {
            return fromComponent;
        }
        const proxy = vnode.component.proxy;
        if (proxy && proxy.leaflet && isLeafletMap(proxy.leaflet) && mapMatchesContainer(proxy.leaflet, container)) {
            return proxy.leaflet;
        }
        return null;
    }

    function discoverExistingMap() {
        const container = document.querySelector('.leaflet-container');
        if (!container || getCapturedLeafletMap(container)) {
            return null;
        }
        const found =
            findMapViaDomLayerScan(container) ||
            findMapViaVueApp(container) ||
            findMapViaDeepObjectScan(container) ||
            findMapViaSiblingVnode(container) ||
            findMapViaMapSibling(container);
        if (found) {
            window.__MRT_CAPTURED_LEAFLET_MAP__ = found;
            window.__MRT_CAPTURED_HAS_LAYER_MANAGER__ = typeof found.getLayerManager === 'function';
            debugLog('H-MAP2', 'discoverExistingMap', 'found existing LiveAtlas map', {
                hasLayerManager: !!found.getLayerManager,
                containerLeafletId: container._leaflet_id,
                layerCount: found._layers ? Object.keys(found._layers).length : 0,
            });
        }
        return found;
    }

    function startRealMapUpgradeWatch() {
        if (window.__MRT_REAL_MAP_WATCH__) {
            return;
        }
        window.__MRT_REAL_MAP_WATCH__ = true;
        let tries = 0;
        const timer = setInterval(function () {
            if (!lastPreviewData) {
                clearInterval(timer);
                window.__MRT_REAL_MAP_WATCH__ = false;
                return;
            }
            discoverExistingMap();
            const container = document.querySelector('.leaflet-container');
            const realMap = getLeafletMap();
            if (realMap && !realMap.__mrtDomAdapter) {
                clearInterval(timer);
                window.__MRT_REAL_MAP_WATCH__ = false;
                previewLayer = null;
                mapLayerHooked = false;
                debugLog('H-MAP3', 'startRealMapUpgradeWatch', 'upgrading preview to real map', {
                    findMethod: lastFindMethod,
                    tries: tries,
                    hasLayerManager: !!realMap.getLayerManager,
                    containerLeafletId: container && container._leaflet_id,
                });
                renderData(lastPreviewData);
                return;
            }
            tries++;
            if (tries >= 240) {
                clearInterval(timer);
                window.__MRT_REAL_MAP_WATCH__ = false;
                debugLog('H-MAP4', 'startRealMapUpgradeWatch', 'gave up waiting for real map', {
                    tries: tries,
                    state: diagnoseMapState(),
                });
            }
        }, 500);
    }

    function mapMatchesContainer(map, container) {
        if (!map || !container) {
            return true;
        }
        try {
            return map.getContainer() === container;
        } catch (e) {
            return false;
        }
    }

    function walkVueVnode(vnode, depth, container) {
        if (!vnode || depth > 100) {
            return null;
        }
        if (vnode.component) {
            const fromComponent = extractLeafletFromVueComponent(vnode.component);
            if (fromComponent && mapMatchesContainer(fromComponent, container)) {
                return fromComponent;
            }
            const fromSubTree = walkVueVnode(vnode.component.subTree, depth + 1, container);
            if (fromSubTree) {
                return fromSubTree;
            }
        }
        const fromChildren = walkVueChildren(vnode.children, depth + 1, container);
        if (fromChildren) {
            return fromChildren;
        }
        if (Array.isArray(vnode.dynamicChildren)) {
            for (let i = 0; i < vnode.dynamicChildren.length; i++) {
                const fromDynamic = walkVueVnode(vnode.dynamicChildren[i], depth + 1, container);
                if (fromDynamic) {
                    return fromDynamic;
                }
            }
        }
        return null;
    }

    function walkVueChildren(children, depth, container) {
        if (!children) {
            return null;
        }
        if (!Array.isArray(children)) {
            return walkVueVnode(children, depth, container);
        }
        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            if (Array.isArray(child)) {
                const fromNested = walkVueChildren(child, depth, container);
                if (fromNested) {
                    return fromNested;
                }
                continue;
            }
            if (child && typeof child === 'object') {
                const fromChild = walkVueVnode(child, depth, container);
                if (fromChild) {
                    return fromChild;
                }
            }
        }
        return null;
    }

    function findMapViaVueApp(container) {
        const appEl = document.getElementById('app');
        const app = appEl && appEl.__vue_app__;
        if (!app || !app._instance) {
            return null;
        }
        return walkVueVnode(app._instance.subTree, 0, container);
    }

    function findMapViaDomPropsScan(container) {
        const roots = [document.getElementById('app'), document.body];
        for (let r = 0; r < roots.length; r++) {
            const root = roots[r];
            if (!root) {
                continue;
            }
            const nodes = root.getElementsByTagName('*');
            for (let i = 0; i < nodes.length; i++) {
                const vue = nodes[i].__vueParentComponent;
                if (!vue) {
                    continue;
                }
                const candidate = extractLeafletFromVueComponent(vue);
                if (candidate && mapMatchesContainer(candidate, container)) {
                    return candidate;
                }
                const fromChain = walkVueParentChain(vue, container);
                if (fromChain) {
                    return fromChain;
                }
            }
        }
        return null;
    }

    function findMapViaVue() {
        const container = document.querySelector('.leaflet-container');
        let el = container;
        while (el) {
            const vue = el.__vueParentComponent;
            if (vue) {
                const candidate = extractLeafletFromVueComponent(vue);
                if (candidate && mapMatchesContainer(candidate, container)) {
                    return candidate;
                }
            }
            el = el.parentElement;
        }
        return null;
    }

    function findMapByContainerRef(container) {
        if (!container) {
            return null;
        }
        for (const key in container) {
            try {
                const val = container[key];
                if (val && val._container === container && typeof val.latLngToLayerPoint === 'function') {
                    return val;
                }
            } catch (e) {
                // ignore expando access errors
            }
        }
        return null;
    }

    function getLeafletMap() {
        const container = document.querySelector('.leaflet-container');
        if (container && !getCapturedLeafletMap(container)) {
            discoverExistingMap();
        }
        const attempts = [
            ['captured', function () { return getCapturedLeafletMap(container); }],
            ['domLayerScan', function () { return findMapViaDomLayerScan(container); }],
            ['vueApp', function () { return findMapViaVueApp(container); }],
            ['deepObjectScan', function () { return findMapViaDeepObjectScan(container); }],
            ['siblingVnode', function () { return findMapViaSiblingVnode(container); }],
            ['mapSibling', function () { return findMapViaMapSibling(container); }],
            ['vueParentChain', function () { return findMapViaVueParentChainScan(container); }],
            ['deepVueScan', function () { return findMapViaDeepVueScan(container); }],
            ['domProps', function () { return findMapViaDomPropsScan(container); }],
            ['vueDomWalk', function () { return findMapViaVue(); }],
            ['containerRef', function () { return findMapByContainerRef(container); }],
            ['legacyExpando', function () {
                if (container && (container._leaflet_map || container._leaflet)) {
                    return container._leaflet_map || container._leaflet;
                }
                return null;
            }],
        ];

        for (let i = 0; i < attempts.length; i++) {
            const method = attempts[i][0];
            const map = attempts[i][1]();
            if (isLeafletMap(map)) {
                lastFindMethod = method;
                return map;
            }
        }

        lastFindMethod = null;
        return null;
    }

    function resolveLiveAtlasMap() {
        const container = document.querySelector('.leaflet-container');
        if (!container) {
            return null;
        }
        const realMap = getLeafletMap();
        if (realMap && !realMap.__mrtDomAdapter) {
            return realMap;
        }
        if (container._leaflet_id && waitAttempts < 120) {
            return null;
        }
        lastFindMethod = 'domAdapter';
        return createDomMapAdapter(container);
    }

    function diagnoseMapState() {
        const container = document.querySelector('.leaflet-container');
        const mapSibling = container && container.previousElementSibling;
        const mapDiv = document.querySelector('.map');
        const realMap = getLeafletMap();
        const resolvedMap = container ? resolveLiveAtlasMap() : null;
        return {
            hasContainer: !!container,
            hasMap: !!resolvedMap,
            hasRealMap: !!realMap,
            hasCapturedMap: !!getCapturedLeafletMap(container),
            captureHookInstalled: !!window.__MRT_MAP_CAPTURE_INSTALLED__,
            usingDomAdapter: !!(resolvedMap && resolvedMap.__mrtDomAdapter),
            hasVueApp: !!(document.getElementById('app') && document.getElementById('app').__vue_app__),
            lastFindMethod: lastFindMethod,
            mapSiblingClass: mapSibling && mapSibling.className,
            mapSiblingHasVue: !!(mapSibling && mapSibling.__vueParentComponent),
            mapDivHasVue: !!(mapDiv && mapDiv.__vueParentComponent),
            containerLeafletId: container && container._leaflet_id,
            liveAtlasZoom: container ? readLiveAtlasZoom(container) : null,
            hasLayerManager: resolvedMap ? typeof resolvedMap.getLayerManager === 'function' : false,
            layerCount: resolvedMap && resolvedMap._layers ? Object.keys(resolvedMap._layers).length : 0,
            hasPolyline: resolvedMap ? !!findPolylineClass(resolvedMap) : false,
        };
    }

    function isClassicDynmapReady() {
        return !!(window.dynmap && window.dynmap.map && typeof window.L !== 'undefined');
    }

    function findPolylineClass(map) {
        if (!map || !map._layers) {
            return null;
        }
        for (const id in map._layers) {
            if (!Object.prototype.hasOwnProperty.call(map._layers, id)) {
                continue;
            }
            const layer = map._layers[id];
            if (layer && typeof layer.getLatLngs === 'function') {
                return layer.constructor;
            }
            if (layer && typeof layer.eachLayer === 'function') {
                let found = null;
                layer.eachLayer(function (child) {
                    if (!found && child && typeof child.getLatLngs === 'function') {
                        found = child.constructor;
                    }
                });
                if (found) {
                    return found;
                }
            }
        }
        return null;
    }

    function findFeatureGroupClass(map) {
        if (!map || !map._layers) {
            return null;
        }
        for (const id in map._layers) {
            if (!Object.prototype.hasOwnProperty.call(map._layers, id)) {
                continue;
            }
            const layer = map._layers[id];
            if (layer && typeof layer.eachLayer === 'function' && typeof layer.addLayer === 'function') {
                return layer.constructor;
            }
        }
        return null;
    }

    function hookMapUpdates(map) {
        if (!map || mapLayerHooked) {
            return;
        }
        mapLayerHooked = true;
        map.on('layeradd', function () {
            if (pendingData) {
                tryRenderPending();
            }
        });
    }

    function buildContext() {
        if (isClassicDynmapReady()) {
            return {
                mode: 'classic',
                dynmap: window.dynmap,
                map: window.dynmap.map,
                L: window.L,
                renderMode: 'polyline',
            };
        }
        const map = resolveLiveAtlasMap();
        if (!map) {
            return null;
        }
        hookMapUpdates(map);
        const PolylineClass = map.__mrtDomAdapter ? null : findPolylineClass(map);
        return {
            mode: 'liveatlas',
            map: map,
            PolylineClass: PolylineClass,
            renderMode: PolylineClass ? 'polyline' : 'svg',
            FeatureGroupClass: map.__mrtDomAdapter ? null : findFeatureGroupClass(map),
        };
    }

    function toContainerPoint(map, latlng) {
        if (typeof map.latLngToContainerPoint === 'function') {
            const pt = map.latLngToContainerPoint(latlng);
            return { x: pt.x, y: pt.y };
        }
        const layerPoint = map.latLngToLayerPoint(latlng);
        const origin = map.containerPointToLayerPoint([0, 0]);
        return { x: layerPoint.x - origin.x, y: layerPoint.y - origin.y };
    }

    function isValidLatLng(latlng) {
        return latlng && Number.isFinite(latlng.lat) && Number.isFinite(latlng.lng);
    }

    function createSvgPreviewLayer(map) {
        const container = map.getContainer();
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('class', 'mrt-dynmap-preview-layer');
        svg.style.position = 'absolute';
        svg.style.left = '0';
        svg.style.top = '0';
        svg.style.width = '100%';
        svg.style.height = '100%';
        svg.style.pointerEvents = 'none';
        svg.style.zIndex = '1000';
        container.appendChild(svg);

        let lines = [];
        let allLatLngs = [];

        function redraw() {
            while (svg.firstChild) {
                svg.removeChild(svg.firstChild);
            }
            const size = map.getSize();
            svg.setAttribute('width', size.x);
            svg.setAttribute('height', size.y);
            svg.setAttribute('viewBox', '0 0 ' + size.x + ' ' + size.y);

            let samplePath = null;
            for (let li = 0; li < lines.length; li++) {
                const line = lines[li];
                const pts = [];
                for (let i = 0; i < line.latlngs.length; i++) {
                    const cp = toContainerPoint(map, line.latlngs[i]);
                    pts.push(cp);
                }
                if (pts.length < 2) {
                    continue;
                }
                const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                const d = 'M ' + pts.map(function (p) {
                    return p.x + ' ' + p.y;
                }).join(' L ');
                path.setAttribute('d', d);
                path.setAttribute('stroke', line.color);
                path.setAttribute('stroke-width', line.weight);
                path.setAttribute('stroke-opacity', line.opacity);
                path.setAttribute('fill', 'none');
                svg.appendChild(path);
                if (!samplePath) {
                    samplePath = { d: d, first: pts[0], last: pts[pts.length - 1] };
                }
            }
            if (samplePath) {
                debugLog('H-LA3', 'createSvgPreviewLayer', 'redraw sample', {
                    findMethod: lastFindMethod,
                    usingDomAdapter: !!map.__mrtDomAdapter,
                    viewBox: '0 0 ' + size.x + ' ' + size.y,
                    samplePath: samplePath,
                });
            }
        }

        map.on('move zoom resize viewreset', redraw);

        return {
            setLines: function (newLines) {
                lines = newLines;
                allLatLngs = [];
                for (let i = 0; i < newLines.length; i++) {
                    allLatLngs = allLatLngs.concat(newLines[i].latlngs);
                }
                redraw();
            },
            clearLayers: function () {
                lines = [];
                allLatLngs = [];
                redraw();
            },
            getAllLatLngs: function () {
                return allLatLngs;
            },
        };
    }

    function fitMapToLatLngs(map, latlngs) {
        if (!latlngs.length || !map || map.__mrtDomAdapter || typeof map.fitBounds !== 'function') {
            return;
        }
        const valid = latlngs.filter(isValidLatLng);
        if (valid.length < 2) {
            return;
        }
        let minLat = Infinity;
        let maxLat = -Infinity;
        let minLng = Infinity;
        let maxLng = -Infinity;
        for (let i = 0; i < valid.length; i++) {
            const ll = valid[i];
            minLat = Math.min(minLat, ll.lat);
            maxLat = Math.max(maxLat, ll.lat);
            minLng = Math.min(minLng, ll.lng);
            maxLng = Math.max(maxLng, ll.lng);
        }
        map.fitBounds([[minLat, minLng], [maxLat, maxLng]], { padding: [40, 40], maxZoom: 6 });
    }

    function fetchDynmapConfig(callback) {
        if (cachedDynmapConfig) {
            callback(cachedDynmapConfig);
            return;
        }
        const url = new URL('standalone/dynmap_config.json?_=' + Date.now(), window.location.href).href;
        fetch(url, { credentials: 'include' })
            .then(function (resp) {
                if (!resp.ok) {
                    throw new Error('HTTP ' + resp.status);
                }
                return resp.json();
            })
            .then(function (json) {
                cachedDynmapConfig = json;
                callback(json);
            })
            .catch(function (err) {
                log('Dynmap config fetch error: ' + err);
                callback(null);
            });
    }

    function resolveWorldMap(config) {
        let worldName = config.defaultworld || 'new';
        let mapName = config.defaultmap || 'flat';
        const hash = (window.location.hash || '').replace(/^#/, '');
        const atlasPart = hash
            .split(';')
            .filter(function (part) {
                return part && part.indexOf('we_dl_preview=') !== 0;
            })
            .join(';');
        if (atlasPart) {
            const parts = atlasPart.split(';');
            if (parts[0]) {
                worldName = decodeURIComponent(parts[0]);
            }
            if (parts[1]) {
                mapName = decodeURIComponent(parts[1]);
            }
        }
        return { worldName: worldName, mapName: mapName };
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
        const wtp = mapDef.worldtomap || [0, 0, 0, 0, 0, 0, 0, 0, 0];
        return {
            locationToLatLng: function (loc) {
                const lat = wtp[3] * loc.x + wtp[4] * loc.y + wtp[5] * loc.z;
                const lng = wtp[0] * loc.x + wtp[1] * loc.y + wtp[2] * loc.z;
                return {
                    lat: -((tileSize - lat) / (1 << nativeZoomLevels)),
                    lng: lng / (1 << nativeZoomLevels),
                };
            },
        };
    }

    function worldToLatLngClassic(x, y, z) {
        const dynmap = window.dynmap;
        const loc = { x: x, y: y, z: z };
        try {
            if (dynmap && typeof dynmap.getProjection === 'function') {
                const ll = dynmap.getProjection().fromLocationToLatLng(loc);
                if (ll) {
                    return ll;
                }
            }
        } catch (e) {
            // try next
        }
        return null;
    }

    function parseExport(json) {
        if (!json) {
            return [];
        }
        if (Array.isArray(json.submissions)) {
            return json.submissions
                .map(function (s) {
                    return s.line;
                })
                .filter(function (line) {
                    return line && Array.isArray(line.x) && line.x.length >= 2;
                });
        }
        if (json.x && json.y && json.z) {
            return [json];
        }
        return [];
    }

    function ensureLayer(ctx) {
        if (previewLayer) {
            if (typeof previewLayer.clearLayers === 'function') {
                previewLayer.clearLayers();
            }
            return previewLayer;
        }
        if (ctx.mode === 'classic') {
            previewLayer = ctx.L.featureGroup([], { attribution: PREVIEW_LAYER_NAME });
            previewLayer.addTo(ctx.map);
            return previewLayer;
        }
        if (ctx.renderMode === 'svg') {
            previewLayer = createSvgPreviewLayer(ctx.map);
            return previewLayer;
        }
        if (ctx.FeatureGroupClass) {
            previewLayer = new ctx.FeatureGroupClass([]);
            ctx.map.addLayer(previewLayer);
            return previewLayer;
        }
        previewLayer = createSvgPreviewLayer(ctx.map);
        return previewLayer;
    }

    function renderWithContext(ctx, lines, projection) {
        if (previewLayer && typeof previewLayer.clearLayers === 'function') {
            previewLayer.clearLayers();
        } else {
            previewLayer = null;
        }

        const projectedLines = [];
        const allLatLngs = [];

        for (let li = 0; li < lines.length; li++) {
            const line = lines[li];
            const latlngs = [];
            for (let i = 0; i < line.x.length; i++) {
                let ll;
                if (ctx.mode === 'classic') {
                    ll = worldToLatLngClassic(line.x[i], line.y[i], line.z[i]);
                } else {
                    ll = projection.locationToLatLng({ x: line.x[i], y: line.y[i], z: line.z[i] });
                }
                if (ll && isValidLatLng(ll)) {
                    latlngs.push(ll);
                }
            }
            if (latlngs.length < 2) {
                continue;
            }
            projectedLines.push({
                latlngs: latlngs,
                color: line.color || '#00FF00',
                weight: line.weight || 3,
                opacity: line.opacity != null ? line.opacity : 0.85,
                label: line.label,
            });
            allLatLngs.push.apply(allLatLngs, latlngs);
        }

        if (!projectedLines.length) {
            log('No preview points after projection.');
            return 0;
        }

        let drawn = 0;
        if (ctx.renderMode === 'svg' || ctx.mode === 'liveatlas' && !ctx.PolylineClass) {
            const layer = ensureLayer(ctx);
            layer.setLines(projectedLines);
            drawn = projectedLines.length;
        } else {
            const layer = ensureLayer(ctx);
            for (let pi = 0; pi < projectedLines.length; pi++) {
                const line = projectedLines[pi];
                let poly;
                if (ctx.mode === 'classic') {
                    poly = ctx.L.polyline(line.latlngs, {
                        color: line.color,
                        weight: line.weight,
                        opacity: line.opacity,
                    });
                } else {
                    poly = new ctx.PolylineClass(line.latlngs, {
                        color: line.color,
                        weight: line.weight,
                        opacity: line.opacity,
                    });
                }
                if (line.label && typeof poly.bindPopup === 'function') {
                    poly.bindPopup(line.label);
                }
                layer.addLayer(poly);
                drawn++;
            }
        }

        if (drawn > 0) {
            try {
                fitMapToLatLngs(ctx.map, allLatLngs);
            } catch (e) {
                // ignore
            }
            if (ctx.map.__mrtDomAdapter) {
                log('Preview line drawn, but LiveAtlas map handle was not captured. Reload the dynmap tab, then preview again.');
                scheduleCapturedMapRetry();
                startRealMapUpgradeWatch();
            }
        }
        debugLog('H-LA2', 'renderWithContext', 'render complete', {
            mode: ctx.mode,
            renderMode: ctx.renderMode,
            findMethod: lastFindMethod,
            usingDomAdapter: !!(ctx.map && ctx.map.__mrtDomAdapter),
            lineCount: lines.length,
            drawn: drawn,
            sampleLatLng: projectedLines[0] && projectedLines[0].latlngs[0],
        });
        return drawn;
    }

    function scheduleCapturedMapRetry() {
        if (window.__MRT_CAPTURED_MAP_WATCH__) {
            return;
        }
        window.__MRT_CAPTURED_MAP_WATCH__ = true;
        let tries = 0;
        const timer = setInterval(function () {
            const container = document.querySelector('.leaflet-container');
            discoverExistingMap();
            const captured = getCapturedLeafletMap(container) || getLeafletMap();
            const data = lastPreviewData || pendingData;
            if (captured && !captured.__mrtDomAdapter && data) {
                clearInterval(timer);
                window.__MRT_CAPTURED_MAP_WATCH__ = false;
                previewLayer = null;
                mapLayerHooked = false;
                lastFindMethod = 'captured';
                debugLog('H-MAP1', 'scheduleCapturedMapRetry', 'captured map available, re-rendering', {
                    tries: tries,
                });
                renderData(data);
                return;
            }
            tries++;
            if (tries >= 80) {
                clearInterval(timer);
                window.__MRT_CAPTURED_MAP_WATCH__ = false;
            }
        }, 250);
    }

    function renderData(data) {
        lastPreviewData = data;
        const lines = parseExport(data);
        if (!lines.length) {
            log('No preview lines in payload.');
            return 0;
        }
        const ctx = buildContext();
        if (!ctx) {
            return 0;
        }
        if (previewLayer && typeof previewLayer.clearLayers === 'function') {
            previewLayer.clearLayers();
        } else {
            previewLayer = null;
        }
        if (ctx.mode === 'classic') {
            const count = renderWithContext(ctx, lines, null);
            log('Rendered ' + count + ' draft line(s) on classic Dynmap.');
            return count;
        }
        fetchDynmapConfig(function (config) {
            if (!config) {
                log('Cannot render: dynmap config unavailable.');
                return;
            }
            const resolved = resolveWorldMap(config);
            const mapDef = findMapDefinition(config, resolved.worldName, resolved.mapName);
            if (!mapDef) {
                log('Cannot render: map not found ' + resolved.worldName + '/' + resolved.mapName);
                return;
            }
            const count = renderWithContext(ctx, lines, createDynmapProjection(mapDef));
            log('Rendered ' + count + ' draft line(s) on LiveAtlas (' + resolved.worldName + '/' + resolved.mapName + ').');
        });
        return lines.length;
    }

    function tryRenderPending() {
        if (!pendingData) {
            return false;
        }
        discoverExistingMap();
        const ctx = buildContext();
        if (!ctx) {
            waitAttempts++;
            if (waitAttempts % 10 === 0) {
                debugLog('H-LA9', 'tryRenderPending', 'still waiting for map', diagnoseMapState());
            }
            return false;
        }
        debugLog('H-LA8', 'tryRenderPending', 'map ready in page context', {
            mode: ctx.mode,
            renderMode: ctx.renderMode,
            findMethod: lastFindMethod,
            layerCount: ctx.map && ctx.map._layers ? Object.keys(ctx.map._layers).length : 0,
            hasLayerManager: ctx.map ? typeof ctx.map.getLayerManager === 'function' : false,
        });
        const data = pendingData;
        pendingData = null;
        renderData(data);
        return true;
    }

    function scheduleWait() {
        if (waitTimer) {
            return;
        }
        waitTimer = setInterval(function () {
            if (tryRenderPending()) {
                clearInterval(waitTimer);
                waitTimer = null;
            }
        }, 500);
    }

    function load(data) {
        pendingData = data;
        lastPreviewData = data;
        if (!tryRenderPending()) {
            log('Waiting for LiveAtlas map (page context)...');
            debugLog('H-LA9', 'load', 'initial wait', diagnoseMapState());
            scheduleWait();
        }
    }

    function clearPreview() {
        if (previewLayer && typeof previewLayer.clearLayers === 'function') {
            previewLayer.clearLayers();
        }
    }

    function isReady() {
        return !!buildContext();
    }

    window.__MRT_DYNMAP_PREVIEW__ = {
        load: load,
        clearPreview: clearPreview,
        isReady: isReady,
    };

    window.addEventListener('mrt-preview-load', function (event) {
        if (event.detail) {
            load(event.detail);
        }
    });

    discoverExistingMap();

    log('Page bootstrap ready.');
})();
