const panelSearch = document.getElementById('cuy-panel-search');

if (panelSearch) {
    const sections = document.querySelectorAll('#cuy-panel-secciones .cuy-panel-module');
    const legacyCards = document.querySelectorAll('#cuy-panel-legacy .cuy-card');
    const status = document.getElementById('cuy-panel-search-status')
        || document.getElementById('cuy-panel-legacy-status');
    const normalize = (text) => text.toLocaleLowerCase('es')
        .normalize('NFD').replace(/\p{M}/gu, '').trim();

    const filterSections = () => {
        const query = normalize(panelSearch.value);
        let matches = 0;

        for (const section of sections) {
            const titleMatches = normalize(section.querySelector('h3').textContent).includes(query);
            let sectionMatches = false;

            for (const item of section.querySelectorAll('li')) {
                const visible = titleMatches || normalize(item.textContent).includes(query);
                item.hidden = !visible;
                sectionMatches ||= visible;
            }

            section.hidden = !sectionMatches;
            if (sectionMatches) matches++;
        }

        for (const card of legacyCards) {
            const visible = normalize(card.textContent).includes(query);
            card.hidden = !visible;
            if (visible) matches++;
        }

        status.textContent = query && matches === 0 ? 'No se encontraron secciones ni enlaces.' : '';
    };

    panelSearch.value = new URLSearchParams(window.location.search).get('q') || '';
    panelSearch.addEventListener('input', filterSections);
    panelSearch.form.addEventListener('submit', (event) => event.preventDefault());
    filterSections();
}
