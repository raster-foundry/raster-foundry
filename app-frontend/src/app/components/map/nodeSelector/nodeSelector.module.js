import angular from 'angular';
import NodeActions from '_redux/actions/node-actions';
import nodeSelectorTpl from './nodeSelector.html';

const NodeSelectorComponent = {
    templateUrl: nodeSelectorTpl,
    controller: 'NodeSelectorController',
    bindings: {
        selected: '<',
        position: '<',
        onSelect: '&',
        onClose: '&'
    }
};

class NodeSelectorController {
    constructor(
        $log, $element, $scope, $timeout, $document, $ngRedux
    ) {
        'ngInject';
        this.$log = $log;
        this.$element = $element;
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$document = $document;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            NodeActions
        )(this);
        $scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        const nodes = state.lab.nodes;

        let nodeArray = nodes ?
            nodes.toArray().filter((node) => {
                return node.type !== 'const';
            }).map(({id, metadata}) => ({id, label: metadata.label})) :
            [];
        this.updateSelected(nodes, this.selected.id ? this.selected.id : this.selected);

        return {
            nodes,
            nodeArray
        };
    }

    $postLink() {
        this.$timeout(() => {
            if (this.position) {
                this.setPosition(this.position);
            }
        }, 0);
    }

    $onChanges(changes) {
        if (changes.position && changes.position.currentValue) {
            this.setPosition(changes.position.currentValue);
        }
        if (changes.selected && changes.selected.currentValue) {
            let selected = changes.selected.currentValue;
            this.updateSelected(this.nodes, selected.id ? selected.id : selected);
            this.$scope.$evalAsync();
            if (this.position) {
                this.$scope.$evalAsync(() => {
                    this.setPosition(this.position);
                });
            }
        }
    }

    updateSelected(nodes, selected) {
        if (selected) {
            const selectedNode = nodes.get(selected);
            this.selectedLabel = selectedNode.metadata.label;
            if (selectedNode.type) {
                this.selectedType = selectedNode.type;
            } else if (selectedNode.apply) {
                this.selectedType = 'function';
            }
        } else {
            delete this.selectedType;
            delete this.selectedLabel;
        }
    }

    setPosition(position) {
        let width = this.$element[0].offsetWidth;
        let percent = position.x * 100;
        let offset;
        if (position.side === 'left') {
            offset = width + position.offset;
        } else if (position.side === 'right' || position.side === 'none') {
            offset = position.offset;
        } else {
            throw new Error(`${position.side} is not a valid side to display a node selector on`);
        }
        let left = `calc(${percent}% ${position.side === 'left' ? '-' : '+'} ${offset}px)`;
        this.$element.css({
            left: left
        });
    }

    startSelecting() {
        let initialClick = true;
        const onClick = () => {
            if (!initialClick) {
                this.showSearch = false;
                this.$document.off('click', this.clickListener);
                this.setPosition(this.position);
                this.$scope.$evalAsync();
            } else {
                initialClick = false;
            }
        };

        if (!this.showSearch) {
            this.showSearch = true;
            this.clickListener = onClick;
            this.$document.on('click', onClick);
            this.$timeout(() => {
                this.setPosition(this.position);
                this.$element.find('.node-filter').focus();
            }, 100);
        } else {
            this.showSearch = false;
            this.setPosition(this.position);
            this.$document.off('click', this.clickListener);
            delete this.clickListener;
        }
    }

    onNodeSelect(node) {
        this.selected = node.id;
        this.onSelect({node: node.id});
        this.showSearch = false;
        this.selectFilter = '';
        this.$timeout(() => this.setPosition(this.position), 100);
    }
}
const NodeSelectorModule = angular.module('components.map.nodeSelector', []);

NodeSelectorModule.component('rfNodeSelector', NodeSelectorComponent);
NodeSelectorModule.controller('NodeSelectorController', NodeSelectorController);

export default NodeSelectorModule;
