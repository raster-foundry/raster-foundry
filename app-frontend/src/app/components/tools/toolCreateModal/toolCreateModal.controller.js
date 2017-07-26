/* global mathjs */

const allowedOps = [
    '+', '-', '*', '/',
    '==', '!=', '>', '<', '>=', '<=',
    'and', 'or', 'xor',
    '^',
    'sqrt', 'log10',
    'ceil', 'floor', 'round',
    'abs',
    'sin', 'cos', 'tan',
    'asin', 'acos', 'atan',
    'sinh', 'cosh', 'tanh',
    'atan2'
];

export default class ToolCreateModalController {
    constructor(
        $scope, $state, uuid4, toolService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$state = $state;
        this.uuid4 = uuid4;
        this.toolService = toolService;
    }

    $onInit() {
        this.toolBuffer = {
            title: '',
            description: '',
            visibility: 'PRIVATE'
        };
        this.isProcessing = false;
        this.definitionExpression = '';
    }

    toolIsPublic() {
        return this.toolBuffer.visibility === 'PUBLIC';
    }

    createTool() {
        this.currentError = '';
        this.isProcessing = true;
        this.commonSymbols = [];
        let expressionTree = null;
        try {
            expressionTree = mathjs.parse(this.definitionExpression);
            this.toolBuffer.definition = this.transformNode(expressionTree);
            this.toolService.createTool(this.toolBuffer).then(tool => {
                this.dismiss();
                this.$state.go('lab.run', { toolid: tool.id });
            });
        } catch (e) {
            this.currentError = 'The tool definition is not valid';
            this.isProcessing = false;
        }
    }

    onVisibilityChange(value) {
        const vis = value ? 'PUBLIC' : 'PRIVATE';
        this.toolBuffer.visibility = vis;
    }

    closeWithData(data) {
        this.close({ $value: data });
    }

    getSymbolId(symbol) {
        const s = this.commonSymbols[symbol];
        if (s) {
            return s.id;
        }
        let id = this.uuid4.generate();
        this.commonSymbols[symbol] = { id };
        return id;
    }

    transformNode(node, value) {
        let builtNode = {
            metadata: {}
        };

        if (node.op && allowedOps.indexOf(node.op) >= 0) {
            // Op node
            builtNode.id = this.uuid4.generate();
            builtNode.apply = node.op;
            builtNode.metadata.label = node.fn;
            builtNode.args = node.args.map(n => this.transformNode(n));
        } else if (node.fn && node.fn.name && allowedOps.indexOf(node.fn.name) >= 0) {
            // Function node; handle nearly the same as op node, for now
            builtNode.id = this.uuid4.generate();
            builtNode.apply = node.fn.name;
            builtNode.metadata.label = node.fn.name;
            builtNode.args = node.args.map(n => this.transformNode(n));
        } else if (node.name) {
            // Symbol node
            if (node.object && node.value) {
                // Symbol with a default (constant)
                return this.transformNode(node.object, node.value.value);
            }
            // Symbol without a default yet (constant or not)
            let isConst = node.name.startsWith('_');
            if (isConst) {
                // Constant symbols without a default yet
                builtNode.type = 'const';
                builtNode.metadata.label = node.name.substring(1);
                if (value) {
                    builtNode.constant = parseFloat(value);
                }
            } else {
                // Raster symbolds
                builtNode.type = 'src';
                builtNode.metadata.label = node.name;
            }
            builtNode.id = this.getSymbolId(builtNode.metadata.label);
        } else if (node.value) {
            builtNode.id = this.uuid4.generate();
            builtNode.type = 'const';
            builtNode.constant = node.value;
            builtNode.metadata.label = node.value;
        } else if (node.content) {
            return this.transformNode(node.content);
        }

        return builtNode;
    }

    isValid() {
        this.currentParsingError = '';
        const parensBalanced = this.validateParenDepth();
        if (!parensBalanced) {
            this.currentParsingError = 'The parentheses in this expression are not balanced';
        }
        return this.toolBuffer.title && this.definitionExpression && parensBalanced;
    }

    validateParenDepth() {
        const expressionArray = this.definitionExpression.split('');
        const result = expressionArray.reduce((acc, c) => {
            if (acc.continue) {
                let d = acc.depth;
                if (c === '(') {
                    d += 1;
                } else if (c === ')') {
                    d -= 1;
                }
                return { depth: d, continue: d >= 0};
            }
            return acc;
        }, { depth: 0, continue: true });
        return result.continue && result.depth === 0;
    }

}
