// Teclado en iPhone/iPad.
//
// Safari en iOS solo abre el teclado cuando el dedo toca un campo de texto real o cuando un campo
// recibe el foco en ciertos momentos del gesto. Compose pinta en un canvas y enfoca su campo oculto
// (`.compose-backing-field`) por programa, y en iOS eso no abre el teclado.
//
// Solución: encima de cada campo de texto de Compose se coloca un <input> real transparente (las
// posiciones salen de la capa de accesibilidad de Compose, que replica cada campo con
// role="textbox"). El dedo toca ese input real, así que iOS abre el teclado. Los eventos del dedo se
// reenvían al canvas, de modo que Compose ve el toque donde se hizo (enfoca su campo, mueve el
// cursor o pulsa el icono que hubiera) y, cuando el input real recibe el foco, se le pasa a Compose,
// con el teclado ya abierto. Si Compose no enfoca ningún campo (p. ej. uno de solo lectura), el
// input real se suelta para no dejar el teclado abierto.
//
// Con `?diagnostico=teclado` en la dirección se activa en cualquier navegador, los inputs se ven
// con un borde y se muestra en pantalla lo que ocurre.
(function () {
    var ua = navigator.userAgent;
    var isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    var diagnostics = /[?&]diagnostico=teclado/.test(location.search);
    if (!isIOS && !diagnostics) return;

    var FORWARDED = ['pointerdown', 'pointermove', 'pointerup', 'pointercancel'];
    var overlays = [];
    var panel = null;
    // Con ratón el foco llega antes de soltar el botón; con el dedo, después. Si el puntero sigue
    // abajo, el traspaso de foco espera a que Compose reciba el `pointerup`.
    var pointerDown = false;
    var pendingHandOver = null;

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

    function composeShadowRoot() {
        var nodes = document.querySelectorAll('#composeTarget *');
        for (var i = 0; i < nodes.length; i++) {
            if (nodes[i].shadowRoot) return nodes[i].shadowRoot;
        }
        return null;
    }

    function describe(element) {
        if (!element) return 'ninguno';
        return element.tagName + (element.className ? '.' + element.className : '') + (element.id ? '#' + element.id : '');
    }

    /** Reenvía al canvas de Compose un evento de puntero recibido en un input superpuesto. */
    function forward(event) {
        var root = composeShadowRoot();
        var canvas = root && root.querySelector('canvas');
        if (!canvas) return;
        canvas.dispatchEvent(new PointerEvent(event.type, {
            bubbles: true,
            cancelable: true,
            composed: true,
            clientX: event.clientX,
            clientY: event.clientY,
            screenX: event.screenX,
            screenY: event.screenY,
            pointerId: event.pointerId,
            pointerType: event.pointerType,
            isPrimary: event.isPrimary,
            button: event.button,
            buttons: event.buttons,
            width: event.width,
            height: event.height,
            pressure: event.pressure,
        }));
    }

    /** El input real tiene el foco (y el teclado): se le pasa a Compose, o se suelta. */
    function handOver(overlay) {
        var root = composeShadowRoot();
        var backing = root && root.querySelector('.compose-backing-field');
        if (backing && root.activeElement === backing) {
            // Compose ya enfocó su campo con el toque reenviado: se le devuelve el foco.
            backing.focus({ preventScroll: true });
            log('teclado abierto · foco devuelto a Compose');
        } else if (backing) {
            backing.focus({ preventScroll: true });
            log('foco pasado al campo de Compose (no estaba activo)');
        } else {
            overlay.blur();
            log('Compose no enfocó ningún campo: se suelta el teclado');
        }
    }

    function createOverlay() {
        var input = document.createElement('input');
        input.type = 'text';
        input.tabIndex = -1;
        input.setAttribute('aria-hidden', 'true');
        input.setAttribute('autocomplete', 'off');
        input.setAttribute('autocorrect', 'off');
        input.setAttribute('autocapitalize', 'off');
        input.setAttribute('spellcheck', 'false');
        // Transparente pero tocable; 16 px para que Safari no amplíe la página al enfocar.
        input.style.cssText = 'position:fixed;margin:0;padding:0;border:0;background:transparent;color:transparent;' +
            'caret-color:transparent;font-size:16px;z-index:2147483646;-webkit-tap-highlight-color:transparent;' +
            // Los arrastres que empiezan sobre un campo los gestiona Compose (desplazar), no Safari.
            'touch-action:none;' +
            // Opacidad completa: Safari no abre el teclado en campos (casi) transparentes por opacidad.
            // Lo que lo hace invisible es el fondo, el texto y el cursor transparentes.
            'outline:none;opacity:1;' + (diagnostics ? 'outline:2px solid #7CFC9A;background:rgba(124,252,154,.25);' : '');
        FORWARDED.forEach(function (type) {
            input.addEventListener(type, function (event) {
                if (type === 'pointerdown') pointerDown = true;
                forward(event);
                if (type === 'pointerup' || type === 'pointercancel') {
                    pointerDown = false;
                    if (pendingHandOver) {
                        var target = pendingHandOver;
                        pendingHandOver = null;
                        handOver(target);
                    }
                }
            });
        });
        input.addEventListener('focus', function () {
            log('input real enfocado por el toque');
            if (pointerDown) pendingHandOver = input; else handOver(input);
        });
        // Lo que se escriba en el input real (si llegara algo) no debe quedarse en él.
        input.addEventListener('input', function () { input.value = ''; });
        document.body.appendChild(input);
        return input;
    }

    /** Coloca un input real sobre cada campo de texto visible de Compose. */
    function sync() {
        var root = composeShadowRoot();
        var fields = root ? root.querySelectorAll('[role="textbox"]') : [];
        var rects = [];
        for (var i = 0; i < fields.length; i++) {
            var rect = fields[i].getBoundingClientRect();
            if (rect.width > 0 && rect.height > 0) rects.push(rect);
        }
        while (overlays.length < rects.length) overlays.push(createOverlay());
        while (overlays.length > rects.length) {
            var removed = overlays.pop();
            if (document.activeElement === removed) removed.blur();
            removed.remove();
        }
        for (var j = 0; j < rects.length; j++) {
            var style = overlays[j].style;
            var r = rects[j];
            style.left = r.left + 'px';
            style.top = r.top + 'px';
            style.width = r.width + 'px';
            style.height = r.height + 'px';
        }
        requestAnimationFrame(sync);
    }

    log('activo · iOS=' + isIOS + ' · ' + ua);
    requestAnimationFrame(sync);

    if (diagnostics) {
        document.addEventListener('focusin', function (event) { log('focusin: ' + describe(event.target)); }, true);
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', function () {
                log('viewport visible: ' + Math.round(window.visualViewport.height) + ' px de alto (si baja, el teclado se abrió)');
            });
        }
    }
})();
