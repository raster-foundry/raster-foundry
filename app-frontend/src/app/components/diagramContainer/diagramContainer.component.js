export default {
    template: '<div class="main lab-workspace"></div>',
    controller: 'DiagramContainerController',
    bindings: {
        shapes: '<?',
        cellLabel: '<?',
        onCellClick: '&',
        onPaperClick: '&'
    }
};
