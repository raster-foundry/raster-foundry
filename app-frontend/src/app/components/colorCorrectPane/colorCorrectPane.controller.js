export default class ColorCorrectController {
    constructor() {
        'ngInject';
    }

    $onInit() {
        let baseFilterOpts = {
            floor: -100,
            ceil: 100,
            onEnd: (id, val) => this.onFilterChange(id, val)
        };
        this.redOpts = Object.assign({}, baseFilterOpts, {id: 'red'});
        this.greenOpts = Object.assign({}, baseFilterOpts, {id: 'green'});
        this.blueOpts = Object.assign({}, baseFilterOpts, {id: 'blue'});
        this.brightOpts = Object.assign({}, baseFilterOpts, {id: 'brightness'});
        this.ctrstOpts = Object.assign({}, baseFilterOpts, {id: 'contrast'});

        this.zeroesCorrection = {
            red: 0,
            green: 0,
            blue: 0,
            brightness: 0,
            contrast: 0
        };
        // On init, the desired correction, if it exists, becomes the correction that
        // we reset to.
        if (this.desiredCorrection) {
            // Allow external users to pass in just the attributes they want.
            this.correction = Object.assign({}, this.zeroesCorrection, this.desiredCorrection);
            this.resetCorrection = Object.assign({}, this.correction);
        } else {
            this.correction = Object.assign({}, this.zeroesCorrection);
            this.resetCorrection = Object.assign({}, this.correction);
        }
    }

    $onChanges(changesObj) {
        if ('reset' in changesObj && this.resetCorrection) {
            this.correction = Object.assign({}, this.resetCorrection);
            this.onCorrectionChange({newCorrection: Object.assign({}, this.correction)});
        }
    }

    onFilterChange(id, val) {
        this.correction[id] = val;
        this.onCorrectionChange({newCorrection: Object.assign({}, this.correction)});
    }
}
