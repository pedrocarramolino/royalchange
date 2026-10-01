// Teclado en iPhone/iPad.
//
// Safari en iOS solo abre el teclado si un campo editable recibe el foco dentro del propio toque.
// Compose pinta en un canvas y enfoca su campo oculto (`.compose-backing-field`) un instante
// después, fuera del gesto, así que el teclado no aparece.
//
// Solución: al terminar el toque (todavía dentro del gesto), si el dedo cae sobre un campo de texto
// —se sabe por la capa de accesibilidad de Compose, que replica cada campo con role="textbox"—, se
// enfoca un campo invisible en ese punto. iOS abre el teclado y, cuando Compose enfoca su campo,
// el teclado se queda. Si ningún campo de Compose toma el foco (p. ej. uno de solo lectura), el
// campo invisible se suelta enseguida para no dejar el teclado abierto.
(function () {
    var ua = navigator.userAgent;
    var isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    if (!isIOS) return;

    var RELEASE_AFTER_MS = 600;
    var primer = null;

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

    document.addEventListener('touchend', function (event) {
        var root = composeShadowRoot();
        var touch = event.changedTouches && event.changedTouches[0];
        if (!root || !touch) return;

        // Compose ya enfocó su campo dentro del gesto: el teclado se abre solo.
        var active = root.activeElement;
        if (active && active.classList && active.classList.contains('compose-backing-field')) return;

        var fields = root.querySelectorAll('[role="textbox"]');
        for (var i = 0; i < fields.length; i++) {
            if (!isInside(fields[i].getBoundingClientRect(), touch.clientX, touch.clientY)) continue;
            var input = getPrimer();
            input.style.left = touch.clientX + 'px';
            input.style.top = touch.clientY + 'px';
            input.focus({ preventScroll: true });
            setTimeout(function () {
                if (document.activeElement === input) input.blur();
            }, RELEASE_AFTER_MS);
            return;
        }
    }, true);
})();
