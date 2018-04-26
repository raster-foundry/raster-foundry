/* global mathjs */
import angular from 'angular';
import _ from 'lodash';

import nodeCreateSidebarTpl from './nodeCreateSidebar.html';

import NodeActions from '_redux/actions/node-actions';

const NodeCreateSidebarComponent = {
    templateUrl: nodeCreateSidebarTpl,
    controller: 'NodeCreateSidebarController'
};

const allowedOps = [
    {op: '+', label: 'Add', minArgs: 2},
    {op: '-', label: 'Subtract', minArgs: 2},
    {op: '*', label: 'Multiply', minArgs: 2},
    {op: '/', label: 'Divide', minArgs: 2},
    {op: '==', label: 'Equals', minArgs: 2, maxArgs: 2},
    {op: '!=', label: 'Does not equal', minArgs: 2, maxArgs: 2},
    {op: '>', label: 'Greater than', minArgs: 2, maxArgs: 2},
    {op: '<', label: 'Less than', minArgs: 2, maxArgs: 2},
    {op: '>=', label: 'Greater than or equal', minArgs: 2, maxArgs: 2},
    {op: '<=', label: 'Less than or equal', minArgs: 2, maxArgs: 2},
    {op: 'and', label: 'Bitwise AND', minArgs: 2},
    {op: 'or', label: 'Bitwise OR', minArgs: 2},
    {op: 'xor', label: 'Bitwise XOR', minArgs: 2},
    {op: '^', label: 'Power', minArgs: 2, maxArgs: 2},
    {op: 'sqrt', label: 'Square root', minArgs: 1, maxArgs: 1},
    {op: 'log10', label: 'Log base 10', minArgs: 1, maxArgs: 1},
    {op: 'ceil', label: 'Ceiling', minArgs: 1, maxArgs: 1},
    {op: 'floor', label: 'Floor', minArgs: 1, maxArgs: 1},
    {op: 'round', label: 'Round', minArgs: 1, maxArgs: 1},
    {op: 'abs', label: 'Absolute value', minArgs: 1, maxArgs: 1},
    {op: 'sin', label: 'Sine', minArgs: 1, maxArgs: 1},
    {op: 'cos', label: 'Cosine', minArgs: 1, maxArgs: 1},
    {op: 'tan', label: 'Tangent', minArgs: 1, maxArgs: 1},
    {op: 'asin', label: 'Arcsine', minArgs: 1, maxArgs: 1},
    {op: 'acos', label: 'Arccosine', minArgs: 1, maxArgs: 1},
    {op: 'atan', label: 'Arctangent', minArgs: 1, maxArgs: 1},
    {op: 'sinh', label: 'Hyperbolic sine', minArgs: 1, maxArgs: 1},
    {op: 'cosh', label: 'Hyperbolic cosine', minArgs: 1, maxArgs: 1},
    {op: 'tanh', label: 'Hyberbolic tangent', minArgs: 1, maxArgs: 1},
    {op: 'atan2', label: 'Arctangent 2', minArgs: 2, maxArgs: 2}
];

const pageSize = 10;

class NodeCreateSidebarController {
    constructor(
        $ngRedux, $scope, $log,
        authService, projectService, templateService, analysisService, mamlService
    ) {
        'ngInject';

        this.$log = $log;
        this.authService = authService;
        this.projectService = projectService;
        this.templateService = templateService;
        this.analysisService = analysisService;
        this.mamlService = mamlService;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            NodeActions
        )(this);
        $scope.$on('$destroy', unsubscribe);

        $scope.$watch('$ctrl.createNode', (cn) => {
            if (cn === 'project') {
                this.fetchProjectPage();
            } else if (cn === 'template') {
                this.fetchTemplatePage();
            }
        });

        $scope.$watch('$ctrl.mamlExpression', (mamlExpression) => {
            if (mamlExpression) {
                this.parseMaml(mamlExpression);
            } else {
                delete this.maml;
            }
        });
    }

    mapStateToThis(state) {
        let selector = {
            createNode: state.lab.createNode,
            createNodeSelection: state.lab.createNodeSelection,
            workspace: state.lab.workspace
        };
        return selector;
    }

    $onInit() {
        this.functionNodes = allowedOps;
        this.opChars = allowedOps.map((ao) => ao.op);
    }

    createSearchParams(searchVal, page) {
        let params = {
            sort: 'createdAt,desc',
            pageSize: pageSize,
            page: page - 1
        };
        if (searchVal) {
            params.search = searchVal;
        }
        return params;
    }

    fetchProjectPage(searchVal, page = 1) {
        if (this.loadingList) {
            return;
        }
        const params = this.createSearchParams(searchVal, page);
        this.itemList = [];
        delete this.listLoadError;
        this.loadingList = true;
        this.currentPageLoader = this.fetchTemplatePage;
        this.projectService.query(params).then((projectResult) => {
            this.updatePagination(projectResult);
            this.currentPage = page;
            this.itemList = projectResult.results;
            this.loadingList = false;
        }, () => {
            this.listLoadError = 'Error requesting projects';
            this.loadingList = false;
        });
    }

    fetchTemplatePage(searchVal, page = 1) {
        if (this.loadingList) {
            return;
        }
        const params = this.createSearchParams(searchVal, page);
        this.itemList = [];
        delete this.listLoadError;
        this.loadingList = true;
        this.currentPageLoader = this.fetchTemplatePage;
        this.templateService.fetchTemplates(params).then((templateResult) => {
            this.updatePagination(templateResult);
            this.currentPage = page;
            this.itemList = templateResult.results;
            this.loadingList = false;
        }, () => {
            this.listLoadError = 'Error requesting templates';
            this.loadingList = false;
        });
    }

    updatePagination(data) {
        this.pagination = {
            pageSize: data.pageSize,
            show: data.count > data.pageSize,
            count: data.count, currentPage: data.page + 1,
            startingItem: data.page * data.pageSize + 1,
            endingItem: Math.min((data.page + 1) * data.pageSize, data.count),
            hasNext: data.hasNext,
            hasPrevious: data.hasPrevious
        };
    }

    shouldShowPagination() {
        return this.createNode === 'project' || this.createNode === 'template' &&
            !this.loadingList &&
            this.pagination.count &&
            this.pagination.count > pageSize;
    }

    parseMaml(mamlExpression) {
        this.mamlError = '';
        this.maml = null;
        this.commonSymbols = [];
        let expressionTree = null;
        if (mamlExpression.length) {
            try {
                expressionTree = mathjs.parse(mamlExpression);
                this.maml = this.mamlService.expressionTreeToMAML(expressionTree);
                delete this.mamlError;
            } catch (e) {
                delete this.maml;
                this.mamlError = e.message ? e.message : 'This expression is not valid';
            }
        } else {
            this.mamlError = '';
        }
    }

    addFormulaNode() {
        this.parseMaml(this.mamlExpression);
        if (this.maml) {
            this.authService
                .getCurrentUser()
                .then((user) => {
                    this.selectNodeToCreate({
                        name: '',
                        visibility: 'PRIVATE',
                        organizationId: user.organizationId,
                        executionParameters: _.clone(this.maml),
                        owner: user.id,
                        readonly: false
                    });
                });
        }
    }

    addFunctionNode(opNode) {
        this.authService.getCurrentUser().then((user) => {
            this.selectNodeToCreate(Object.assign(
                this.analysisService.generateAnalysisFromFunction(opNode),
                {
                    visibility: 'PRIVATE',
                    organizationId: user.organizationId,
                    owner: user.id,
                    readonly: false
                }
            ));
        });
    }

    addInputNode(project) {
        this.authService.getCurrentUser().then((user) => {
            this.selectNodeToCreate(Object.assign(
                this.analysisService.generateAnalysisFromInput(project),
                {
                    visibility: 'PRIVATE',
                    organizationId: user.organizationId,
                    owner: user.id,
                    readonly: false
                }
            ));
        });
    }

    addTemplateNode(template) {
        this.authService.getCurrentUser().then((user) => {
            this.selectNodeToCreate(Object.assign(
                this.analysisService.generateAnalysisFromTemplate(template),
                {
                    visibility: 'PRIVATE',
                    organizationId: user.organizationId,
                    owner: user.id,
                    readonly: false
                }
            ));
        });
    }
    addNode(node) {
        switch (this.createNode) {
        case 'project':
            this.addInputNode(node);
            break;
        case 'function':
            this.addFunctionNode(node);
            break;
        case 'template':
            this.addTemplateNode(node);
            break;
        case 'formula':
            this.addFormulaNode(node);
            break;
        default:
            throw new Error('called addNode when not in a project or template list view');
        }
    }

    search(value, nodeType) {
        switch (nodeType) {
        case 'project':
            this.searchProjects(value);
            break;
        case 'function':
        case 'formula':
            this.searchFunctions(value);
            break;
        case 'template':
            this.searchTemplate(value);
            break;
        default:
            throw new Error('not a supported search type.');
        }
    }

    searchProjects(value) {
        if (value) {
            this.fetchProjectPage(value);
        } else {
            this.fetchProjectPage();
        }
    }

    searchFunctions(value) {
        if (value) {
            const lcSearch = value.toLowerCase();
            this.functionNodes = allowedOps.filter((ao) => {
                return ao.op.toLowerCase().includes(lcSearch) ||
                    ao.label.toLowerCase().includes(lcSearch);
            });
        } else {
            this.functionNodes = allowedOps;
        }
    }

    searchTemplate(value) {
        if (value) {
            this.fetchTemplatePage(value);
        } else {
            this.fetchTemplatePage();
        }
    }
}

const NodeCreateSidebarModule = angular.module('components.lab.nodeCreateSidebar', []);

NodeCreateSidebarModule.controller(
    'NodeCreateSidebarController', NodeCreateSidebarController
);
NodeCreateSidebarModule.component(
    'rfNodeCreateSidebar', NodeCreateSidebarComponent
);

export default NodeCreateSidebarModule;
