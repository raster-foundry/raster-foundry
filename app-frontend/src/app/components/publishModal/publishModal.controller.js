export default class PublishModalController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        this.urlMappings = [
            {
                label: 'Standard',
                z: 'z',
                x: 'x',
                y: 'y',
                active: true
            },
            {
                label: 'ArcGIS',
                z: 'level',
                x: 'col',
                y: 'row',
                active: false
            }
        ];

        this.mappedTileUrl =
            this.hydrateTileUrl(this.getActiveMapping());
    }

    onUrlMappingChange(mapping) {
        this.setActiveMappingByLabel(mapping.label);
        this.mappedTileUrl = this.hydrateTileUrl(mapping);
    }

    hydrateTileUrl(mapping) {
        return this.resolve.tileUrl
            .replace('{z}', `{${mapping.z}}`)
            .replace('{x}', `{${mapping.x}}`)
            .replace('{y}', `{${mapping.y}}`);
    }

    getActiveMapping() {
        return this.urlMappings.find(m => m.active);
    }

    setActiveMappingByLabel(label) {
        this.urlMappings.forEach(m => {
            m.active = m.label === label;
        });
    }
}
