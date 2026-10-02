// Toques en el móvil.
//
// Compose marca su canvas con `touch-action: pan-x pan-y`: el navegador se reserva los arrastres.
// Royal Chance ocupa toda la pantalla y Compose desplaza por su cuenta, así que eso sobra, y en
// Safari (iOS) hace daño: si el dedo se mueve un poco al tocar, Safari lo toma como el inicio de un
// desplazamiento de la página, manda `pointercancel` y Compose descarta el toque (el botón "no
// responde al primer toque"). Con `touch-action: none` todos los gestos llegan a Compose. La hoja de
// estilos ya lo pone en los contenedores; aquí se fuerza también en el canvas, por si acaso.
//
// Con `?diagnostico=toques` en la dirección se muestra en pantalla cada toque (y si el navegador lo
// cancela).
(function () {
    var diagnostics = /[?&]diagnostico=toques/.test(location.search);
    var panel = null;

    function log(message) {
        if (!panel) {
            panel = document.createElement('pre');
            panel.style.cssText = 'position:fixed;left:0;right:0;bottom:0;max-height:35%;overflow:auto;margin:0;padding:6px;' +
                'background:rgba(0,0,0,.85);color:#7CFC9A;font:11px/1.3 monospace;z-index:2147483647;pointer-events:none;white-space:pre-wrap;';
            document.body.appendChild(panel);
        }
        var time = new Date().toISOString().substring(17, 23);
        panel.textContent = (time + ' ' + message + '\n' + panel.textContent).substring(0, 3000);
    }

    function composeCanvas() {
        var nodes = document.querySelectorAll('#composeTarget *');
        for (var i = 0; i < nodes.length; i++) {
            var canvas = nodes[i].shadowRoot && nodes[i].shadowRoot.querySelector('canvas');
            if (canvas) return canvas;
        }
        return null;
    }

    function watch(canvas) {
        canvas.style.touchAction = 'none';
        // Si Compose vuelve a escribir el estilo, se corrige.
        new MutationObserver(function () {
            if (canvas.style.touchAction !== 'none') canvas.style.touchAction = 'none';
        }).observe(canvas, { attributes: true, attributeFilter: ['style'] });
        if (diagnostics) {
            log('canvas listo · touch-action: none');
            ['pointerdown', 'pointerup', 'pointercancel'].forEach(function (type) {
                canvas.addEventListener(type, function (event) {
                    log(type + ' ' + event.pointerType + ' ' + Math.round(event.clientX) + ',' + Math.round(event.clientY) +
                        (type === 'pointercancel' ? '  ← CANCELADO POR EL NAVEGADOR' : ''));
                }, true);
            });
        }
    }

    // El canvas aparece cuando arranca Compose: se busca hasta encontrarlo.
    var timer = setInterval(function () {
        var canvas = composeCanvas();
        if (!canvas) return;
        clearInterval(timer);
        watch(canvas);
    }, 200);
})();
