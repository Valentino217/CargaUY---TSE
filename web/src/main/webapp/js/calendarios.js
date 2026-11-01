/* Selector para los campos HTML heredados. Los p:datePicker siguen usando PrimeFaces. */
(() => {
    'use strict';
    const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
        'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    let activo = null;
    const iso = fecha => `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
    const fechaLocal = valor => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(valor)) return null;
        const [ano, mes, dia] = valor.split('-').map(Number);
        const fecha = new Date(ano, mes - 1, dia, 12);
        return iso(fecha) === valor ? fecha : null;
    };
    function cerrar(devolverFoco = true) {
        if (!activo) return;
        const anterior = activo;
        activo = null;
        anterior.panel.remove();
        anterior.trigger.setAttribute('aria-expanded', 'false');
        if (devolverFoco) anterior.trigger.focus();
    }
    function abrir(input, trigger) {
        cerrar(false);
        let seleccion = input.value;
        const hoy = new Date();
        const inicial = fechaLocal(seleccion) || fechaLocal(input.max) || hoy;
        const minimo = fechaLocal(input.min);
        const maximo = fechaLocal(input.max);
        const panel = document.createElement('section');
        panel.className = 'cuy-native-calendar';
        panel.id = 'cuy-calendar-activo';
        panel.setAttribute('role', 'dialog');
        panel.setAttribute('aria-label', 'Elegir fecha');
        trigger.setAttribute('aria-expanded', 'true');

        const caption = document.createElement('p');
        caption.className = 'cuy-calendar-caption';
        caption.textContent = 'Elegí una fecha';
        const opciones = document.createElement('div');
        opciones.className = 'cuy-calendar-options';
        const mes = document.createElement('select');
        mes.setAttribute('aria-label', 'Mes');
        meses.forEach((nombre, i) => mes.add(new Option(nombre, String(i))));
        const ano = document.createElement('select');
        ano.setAttribute('aria-label', 'Año');
        const desde = minimo ? minimo.getFullYear() : 1900;
        const hasta = maximo ? maximo.getFullYear() : Math.max(hoy.getFullYear(), inicial.getFullYear());
        for (let y = hasta; y >= desde; y--) ano.add(new Option(String(y), String(y)));
        mes.value = String(inicial.getMonth());
        ano.value = String(Math.min(hasta, Math.max(desde, inicial.getFullYear())));
        opciones.append(mes, ano);

        const semana = document.createElement('div');
        semana.className = 'cuy-calendar-week';
        ['L', 'M', 'X', 'J', 'V', 'S', 'D'].forEach(dia => {
            const celda = document.createElement('span');
            celda.textContent = dia;
            semana.append(celda);
        });
        const grid = document.createElement('div');
        grid.className = 'cuy-calendar-grid';
        const acciones = document.createElement('div');
        acciones.className = 'cuy-calendar-actions';
        const cancelar = document.createElement('button');
        cancelar.type = 'button'; cancelar.className = 'cuy-calendar-cancel';
        cancelar.textContent = 'Volver'; cancelar.addEventListener('click', () => cerrar());
        const aplicar = document.createElement('button');
        aplicar.type = 'button'; aplicar.className = 'cuy-calendar-apply';
        aplicar.textContent = 'Aplicar';
        aplicar.addEventListener('click', () => {
            input.value = seleccion;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            cerrar();
        });
        acciones.append(cancelar, aplicar);

        function dibujar() {
            grid.replaceChildren();
            const y = Number(ano.value), m = Number(mes.value);
            const primero = new Date(y, m, 1, 12);
            const huecos = (primero.getDay() + 6) % 7;
            for (let i = 0; i < huecos; i++) grid.append(document.createElement('span'));
            const dias = new Date(y, m + 1, 0, 12).getDate();
            for (let d = 1; d <= dias; d++) {
                const valor = iso(new Date(y, m, d, 12));
                const boton = document.createElement('button');
                boton.type = 'button'; boton.className = 'cuy-calendar-day';
                boton.textContent = String(d); boton.dataset.fecha = valor;
                boton.setAttribute('aria-label', `${d} de ${meses[m]} de ${y}`);
                boton.setAttribute('aria-pressed', String(valor === seleccion));
                if (valor === iso(hoy)) boton.setAttribute('aria-current', 'date');
                boton.disabled = Boolean((input.min && valor < input.min) || (input.max && valor > input.max));
                boton.addEventListener('click', () => {
                    seleccion = valor;
                    grid.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(b === boton)));
                    aplicar.disabled = false;
                });
                grid.append(boton);
            }
            aplicar.disabled = !fechaLocal(seleccion) || Boolean((input.min && seleccion < input.min) || (input.max && seleccion > input.max));
        }
        mes.addEventListener('change', dibujar);
        ano.addEventListener('change', dibujar);
        panel.addEventListener('keydown', event => {
            if (event.key === 'Escape') { event.preventDefault(); cerrar(); }
            if (event.target.matches('.cuy-calendar-day')) {
                const salto = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[event.key];
                if (salto) {
                    event.preventDefault();
                    const botones = [...grid.querySelectorAll('button')];
                    const siguiente = botones[botones.indexOf(event.target) + salto];
                    if (siguiente && !siguiente.disabled) siguiente.focus();
                }
            }
        });
        panel.append(caption, opciones, semana, grid, acciones);
        document.body.append(panel);
        activo = { panel, trigger };
        dibujar();
        const rect = input.getBoundingClientRect();
        const ancho = panel.getBoundingClientRect().width;
        const alto = panel.getBoundingClientRect().height;
        panel.style.left = `${Math.max(12, Math.min(rect.left, window.innerWidth - ancho - 12))}px`;
        panel.style.top = `${Math.max(12, Math.min(rect.bottom + 8, window.innerHeight - alto - 12))}px`;
        panel.style.maxHeight = 'calc(100vh - 24px)';
        panel.style.overflowY = 'auto';
        mes.focus();
    }

    function iniciar() {
        document.querySelectorAll('input[type="date"]').forEach(input => {
            if (input.closest('.cuy-native-date')) return;
            const wrapper = document.createElement('span');
            wrapper.className = 'cuy-native-date';
            input.before(wrapper); wrapper.append(input);
            const trigger = document.createElement('button');
            trigger.type = 'button'; trigger.className = 'cuy-calendar-trigger';
            trigger.textContent = '▦'; trigger.setAttribute('aria-label', 'Abrir calendario');
            trigger.setAttribute('aria-haspopup', 'dialog'); trigger.setAttribute('aria-expanded', 'false');
            trigger.setAttribute('aria-controls', 'cuy-calendar-activo');
            trigger.disabled = input.disabled || input.readOnly;
            trigger.addEventListener('click', event => {
                event.preventDefault(); abrir(input, trigger);
            });
            input.addEventListener('click', event => {
                if (input.disabled || input.readOnly) return;
                event.preventDefault(); abrir(input, trigger);
            });
            wrapper.append(trigger);
        });
    }
    document.addEventListener('pointerdown', event => {
        if (activo && !activo.panel.contains(event.target) && !event.target.closest('.cuy-native-date')) cerrar(false);
    });
    window.addEventListener('resize', () => cerrar(false));
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
