import assetLogo from '../../../../assets/images/logo-raster-foundry.png';

class ProjectController {
    constructor($log) {
        'ngInject';
        $log.debug('ProjectController initialized');
        this.assetLogo = assetLogo;
    }
}

export default ProjectController;
