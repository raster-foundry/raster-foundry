/* global mathjs */

const allowedOps = ['+', '-', '*', '/'];

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
        this.isProcessing = true;
        this.commonSymbols = [];
        const expressionTree = mathjs.parse(this.definitionExpression);
        this.toolBuffer.definition = this.transformNode(expressionTree);
        this.toolService.createTool(this.toolBuffer).then(tool => {
            this.dismiss();
            this.$state.go('lab.run', { toolid: tool.id });
        });
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

        if (node.op && allowedOps.indexOf(node.op >= 0)) {
            // Op node
            builtNode.id = this.uuid4.generate();
            builtNode.apply = node.op;
            builtNode.metadata.label = node.fn;
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
        } else if (node.content) {
            return this.transformNode(node.content);
        }

        return builtNode;
    }

    isValid() {
        return this.toolBuffer.title && this.toolBuffer.definitionExpression;
    }

}
