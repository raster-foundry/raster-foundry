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

export default (app) => {
    class MamlService {
        constructor(uuid4) {
            this.uuid4 = uuid4;
        }

        getSymbolId(commonSymbols, symbol) {
            const s = commonSymbols[symbol];
            if (s) {
                return s.id;
            }
            let id = this.uuid4.generate();
            commonSymbols[symbol] = { id };
            return id;
        }

        expressionTreeToMAML(tree, commonSymbols = [], value, allowCollapse = true) {
            let mamlNode = {
                id: this.uuid4.generate(),
                metadata: {}
            };

            switch (tree.type) {
            case 'AssignmentNode':
                return this.expressionTreeToMAML(tree.object, commonSymbols, tree.value.value);
            case 'ConstantNode':
                Object.assign(mamlNode, {
                    type: 'const',
                    constant: tree.value,
                    metadata: {
                        label: tree.value
                    }
                });
                break;
            case 'OperatorNode':
                if (allowedOps.indexOf(tree.op) >= 0) {
                    mamlNode.apply = tree.op;
                    mamlNode.metadata.label = tree.fn;
                    mamlNode.metadata.collapsable = allowCollapse;
                    break;
                }
                return false;
            case 'FunctionNode':
                mamlNode.apply = tree.fn.name;
                mamlNode.metadata.label = tree.fn.name;
                if (mamlNode.apply === 'classify') {
                    const classifications = tree.args[1];
                    tree.args = [ tree.args[0] ];
                    mamlNode.classMap = {
                        classifications: this.objectNodeToClassifications(classifications)
                    };
                }
                break;
            case 'ParenthesisNode':
                return this.expressionTreeToMAML(tree.content, commonSymbols, null, false);
            case 'SymbolNode':
                if (tree.name.startsWith('_')) {
                    mamlNode.type = 'const';
                    mamlNode.metadata.label = tree.name.substring(1);
                    if (value) {
                        mamlNode.constant = parseFloat(value);
                    }
                } else {
                    mamlNode.type = 'projectSrc';
                    mamlNode.metadata.label = tree.name;
                }
                mamlNode.id = this.getSymbolId(commonSymbols, mamlNode.metadata.label);
                break;
            default:
                return false;
            }

            if (tree.args && tree.args.length) {
                mamlNode.args = tree.args.map(a => this.expressionTreeToMAML(a, commonSymbols));
            }

            if (tree.type === 'OperatorNode' && tree.args) {
                mamlNode.args.forEach((c, i) => {
                    if (c.apply && c.apply === mamlNode.apply && c.metadata.collapsable) {
                        mamlNode.args = [
                            ...mamlNode.args.slice(0, i),
                            ...c.args,
                            ...mamlNode.args.slice(i + 1, mamlNode.args.length)
                        ];
                    }
                });
            }
            return mamlNode;
        }

        objectNodeToClassifications(node) {
            return Object.keys(node.properties).reduce((acc, p) => {
                acc[`${parseFloat(p)}`] = parseInt(node.properties[p].value, 10);
                return acc;
            }, {});
        }

        isValid() {
            this.currentParsingError = '';
            const parensBalanced = this.validateParenDepth();
            const bracesBalanced = this.validateBraceDepth();
            if (!parensBalanced) {
                this.currentParsingError = 'The parentheses in this expression are not balanced';
            } else if (!bracesBalanced) {
                this.currentParsingError = 'The braces in this expression are not balanced';
            }
            return this.templateBuffer.name && this.definitionExpression && parensBalanced;
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

        validateBraceDepth() {
            const expressionArray = this.definitionExpression.split('');
            const result = expressionArray.reduce((acc, c) => {
                if (acc.continue) {
                    let d = acc.depth;
                    if (c === '{') {
                        d += 1;
                    } else if (c === '}') {
                        d -= 1;
                    }
                    return { depth: d, continue: d >= 0};
                }
                return acc;
            }, { depth: 0, continue: true });
            return result.continue && result.depth === 0;
        }
    }

    app.service('mamlService', MamlService);
};
