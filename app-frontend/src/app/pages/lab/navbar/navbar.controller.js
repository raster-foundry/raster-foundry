import * as LabActions from '../../../redux/actions/lab-actions';

export default class LabNavbarController {
    constructor(toolService, $state, $uibModal, $ngRedux, $scope) {
        'ngInject';

        this.toolService = toolService;
        this.$state = $state;
        this.$uibModal = $uibModal;
        this.$ngRedux = $ngRedux;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, LabActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
    }

    mapStateToThis(state) {
        return {
            tool: state.lab.tool,
            updating: state.lab.updating
        };
    }

    toggleEdit() {
        this.nameBuffer = this.tool.name;
        this.editing = !this.editing;
    }

    finishEdit() {
        this.updateToolName(this.nameBuffer);
        this.toggleEdit();
    }
}
