// Teclado en iPhone/iPad.
//
// Safari en iOS solo abre el teclado si un campo editable recibe el foco dentro del propio toque.
// Compose pinta en un canvas y enfoca su campo oculto (`.compose-backing-field`) fuera de ese
// momento, así que el teclado no aparece.
//
// Al terminar el toque (todavía dentro del gesto):
// - si Compose ya enfocó su campo oculto (lo habitual), se enfoca un campo invisible y se devuelve
//   el foco a Compose: el cambio a un campo distinto es lo que hace que iOS abra el teclado;
// - si no, y el dedo cae sobre un campo de texto (la capa de accesibilidad de Compose replica cada
//   campo con role="textbox"), se enfoca un campo invisible en ese punto: iOS abre el teclado y,
//   cuando Compose enfoca su campo, el teclado se queda. Si ningún campo de Compose toma el foco
//   (p. ej. uno de solo lectura), el campo invisible se suelta enseguida.
//
// Con `?diagnostico=teclado` en la dirección se activa en cualquier navegador y se muestra en
// pantalla lo que ocurre en cada toque.
(function () {
    var ua = navigator.userAgent;
    var isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    var diagnostics = /[?&]diagnostico=teclado/.test(location.search);
    if (!isIOS && !diagnostics) return;

    var RELEASE_AFTER_MS = 600;
    var primer = null;
    var panel = null;

    function log(message) {
        if (!diagnostics) return;
        if (!panel) {
            panel = document.createElement('pre');
            panel.style.cssText = 'position:fixed;left:0;right:0;bottom:0;max-height:40%;overflow:auto;margin:0;padding:6px;' +
                'background:rgba(0,0,0,.85);color:#7CFC9A;font:11px/1.3 monospace;z-index:2147483647;pointer-events:none;white-space:pre-wrap;';
            document.body.appendChild(panel);
        }
        var time = new Date().toISOString().substring(17, 23);
        panel.textContent = (time + ' ' + message + '\n' + panel.textContent).substring(0, 3000);
    }

    function getPrimer() {
        if (primer) return primer;
        primer = document.createElement('input');
        primer.type = 'text';
        primer.tabIndex = -1;
        primer.setAttribute('aria-hidden', 'true');
        primer.setAttribute('autocomplete', 'off');
        // 16 px como mínimo: con menos, Safari amplía la página al enfocar.
        primer.style.cssText = 'position:fixed;left:0;top:0;width:1px;height:1px;padding:0;border:0;' +
            'opacity:0;pointer-events:none;font-size:16px;';
        document.body.appendChild(primer);
        return primer;
    }

    function composeShadowRoot() {
        var nodes = document.querySelectorAll('#composeTarget *');
        for (var i = 0; i < nodes.length; i++) {
            if (nodes[i].shadowRoot) return nodes[i].shadowRoot;
        }
        return null;
    }

    function isInside(rect, x, y) {
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    function describe(element) {
        if (!element) return 'ninguno';
        return element.tagName + (element.className ? '.' + element.className : '') + (element.id ? '#' + element.id : '');
    }

    log('activo · iOS=' + isIOS + ' · ' + ua);

    document.addEventListener('touchend', function (event) {
        var root = composeShadowRoot();
        var touch = event.changedTouches && event.changedTouches[0];
        if (!root || !touch) {
            log('toque sin raíz de Compose o sin coordenadas');
            return;
        }
        var x = Math.round(touch.clientX);
        var y = Math.round(touch.clientY);
        var fields = root.querySelectorAll('[role="textbox"]');
        var backing = root.querySelector('.compose-backing-field');
        var active = root.activeElement;
        log('toque ' + x + ',' + y + ' · campos=' + fields.length + ' · oculto=' + describe(backing) + ' · foco=' + describe(active));

        // Compose ya enfocó su campo, pero iOS no abrió el teclado. Volver a enfocar el mismo campo
        // no basta (para iOS ya tenía el foco): se pasa por el campo invisible, que lo abre, y se
        // devuelve el foco a Compose, todo dentro del gesto.
        if (backing && active === backing) {
            var bridge = getPrimer();
            bridge.style.left = x + 'px';
            bridge.style.top = y + 'px';
            bridge.focus({ preventScroll: true });
            backing.focus({ preventScroll: true });
            log('foco: invisible → Compose dentro del gesto (foco=' + describe(root.activeElement) + ')');
            return;
        }

        for (var i = 0; i < fields.length; i++) {
            if (!isInside(fields[i].getBoundingClientRect(), x, y)) continue;
            var input = getPrimer();
            input.style.left = x + 'px';
            input.style.top = y + 'px';
            input.focus({ preventScroll: true });
            log('campo invisible enfocado (foco=' + describe(document.activeElement) + ')');
            setTimeout(function () {
                var nowBacking = root.querySelector('.compose-backing-field');
                log('tras ' + RELEASE_AFTER_MS + ' ms · foco=' + describe(document.activeElement) +
                    ' · foco en Compose=' + describe(root.activeElement) + ' · oculto=' + describe(nowBacking));
                if (document.activeElement === input) input.blur();
            }, RELEASE_AFTER_MS);
            return;
        }
        log('el toque no cae sobre ningún campo de texto');
    }, true);

    if (diagnostics) {
        document.addEventListener('focusin', function (event) { log('focusin: ' + describe(event.target)); }, true);
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', function () {
                log('viewport visible: ' + Math.round(window.visualViewport.height) + ' px de alto (si baja, el teclado se abrió)');
            });
        }
    }
})();
