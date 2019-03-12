import tpl from './index.html';

const component = {
    templateUrl: tpl
};

export default angular
    .module('components.pages.share.project.error', [])
    .component('rfShareProjectErrorPage', component).name;
