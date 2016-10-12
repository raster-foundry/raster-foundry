export default class BucketItemController {
    constructor($scope, $attrs) {
        'ngInject';

        this.isSelectable = $attrs.hasOwnProperty('selectable');
        $scope.$watch(
            () => this.selected({bucket: this.bucket}),
            (selected) => {
                this.selectedStatus = selected;
            }
        );
    }

    toggleSelected(event) {
        this.onSelect({bucket: this.bucket, selected: !this.selectedStatus});
        event.stopPropagation();
    }
}
