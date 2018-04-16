/* global joint */
import _ from 'lodash';
import {Map, Set, Stack} from 'immutable';

export function getNodeRoots(linksBySource, nodeId) {
    let parents = new Stack([nodeId]);
    let roots = new Set();
    const addToParents = (link) => {
        parents = parents.push(link);
    };
    let currentNode = parents.peek();
    do {
        parents = parents.shift();

        if (linksBySource.has(currentNode)) {
            linksBySource.get(currentNode)
                .keySeq()
                .forEach(addToParents);
        } else {
            roots = roots.add(currentNode);
        }

        currentNode = parents.peek();
    } while (currentNode);

    return roots;
}

export function getNodeLeafs(linksByTarget, nodeId) {
    let sources = new Stack([nodeId]);
    let leafs = new Set();
    const addToSources = (link) => {
        sources = sources.push(link);
    };
    let currentNode = sources.peek();
    do {
        sources = sources.shift();

        if (linksByTarget.has(currentNode)) {
            linksByTarget.get(currentNode)
                .keySeq()
                .forEach(addToSources);
        } else {
            leafs = leafs.add(currentNode);
        }

        currentNode = sources.peek();
    } while (currentNode);

    return leafs;
}

export function astNodeFromNodeId(nodeMap, nodeId) {
    let node = nodeMap.get(nodeId);
    let astNode = {
        id: nodeId,
        metadata: node.metadata
    };
    /* eslint-disable no-undefined */
    let optionalAttributes = {
        apply: node.operation,
        args: node.operation ? node.inputIds || [] : undefined,
        type: node.type !== 'function' ? node.type : undefined,
        projId: node.projId,
        band: node.band
    };
    /* eslint-enable no-undefined */
    return Object.assign(astNode, _.omitBy(optionalAttributes, _.isUndefined));
}

export function astFromRootId(nodes, rootId) {
    let rootNode = astNodeFromNodeId(nodes, rootId);
    let stack = [rootNode];

    while (stack.length) {
        let currentNode = stack.pop();
        if (currentNode.args && currentNode.args.length) {
            currentNode.args = currentNode.args.map(arg => {
                let node = astNodeFromNodeId(nodes, arg);
                return node;
            });
            stack = stack.concat(currentNode.args);
        }
    }
    return rootNode;
}

export function astFromRootNode(nodes, root) {
    let rootNode = _.clone(root);
    let stack = [rootNode];

    while (stack.length) {
        let currentNode = stack.pop();
        delete currentNode.analysisId;
        delete currentNode.parent;
        if (currentNode.args && currentNode.args.length) {
            currentNode.args = currentNode.args.map(arg => {
                let node = Object.assign({}, nodes.get(arg), {parent: currentNode});
                return node;
            });
            stack = stack.concat(currentNode.args);
        }
    }

    return rootNode;
}

export function getNodeArgs(node) {
    if (node.args) {
        if (Array.isArray(node.args)) {
            return [...node.args];
        }
        return Object.values(node.args);
    }
    return [];
}

export function nodesFromAnalysis(analysis) {
    let nodes = new Map();
    let json = _.cloneDeep(analysis.executionParameters);
    let stack = [json];
    while (stack.length) {
        let node = Object.assign({}, stack.pop());
        let args = getNodeArgs(node);
        Object.assign(node, {args: args.map(arg => arg.id)}, {analysisId: analysis.id});
        nodes = nodes.set(node.id, node);
        if (node.args && node.args.length) {
            stack = stack.concat(
                args.map((a) => {
                    return Object.assign({
                        parent: node.id
                    }, a);
                })
            );
        }
    }
    return nodes;
}

export function getNodeDefinition(state, context) {
    if (context.nodeId && state.lab.nodes) {
        return state.lab.nodes.get(context.nodeId);
    }
    return false;
}

export function getNodeHistogram(state, context) {
    if (context.nodeId && state.lab.histograms) {
        return state.lab.histograms.get(context.nodeId);
    }
    return false;
}

export function nodeIsChildOf(linkedNodeId, inputNodeId, nodes) {
    if (linkedNodeId === inputNodeId) {
        return true;
    }
    let stack = [nodes.get(inputNodeId)];
    while (stack.length) {
        let node = stack.pop();
        let numArgs = node.args && node.args.length ? node.args.length : 0;
        for (let i = 0; i < numArgs; i = i + 1) {
            let argId = node.args[i];
            if (argId === linkedNodeId) {
                return true;
            }
            stack.push(nodes.get(argId));
        }
    }
    return false;
}

export function createPorts(inputs, outputs) {
    let ports = [];
    let inputList = Array.isArray(inputs) ?
        inputs : Array(inputs).fill();

    ports = inputList.map((input, idx) => {
        return {
            id: `input-${idx}`,
            label: `input-${idx}`,
            group: 'inputs'
        };
    });

    ports = ports.concat(outputs.map(o => {
        return {
            id: o,
            group: 'outputs'
        };
    }));

    return ports;
}

export function getNodeLabel(json) {
    if (json.metadata && json.metadata.label) {
        return json.metadata.label;
    }
    return json.apply;
}

export function getNodeType(node) {
    if (node.type) {
        return node.type;
    } else if (node.apply === 'classify') {
        return 'classify';
    }
    return 'function';
}

export function getNodeAttributes(analysis, node) {
    let rectInputs = getNodeArgs(node).length;
    let rectOutputs = ['Output'];
    let ports = createPorts(rectInputs, rectOutputs);
    let inputIds = [];
    if (node.args) {
        if (Array.isArray(node.args)) {
            inputIds = [...node.args].map((arg) => arg.id);
        }
        inputIds = Object.values(node.args).map((arg) => arg.id);
    }

    let nodeAttributes = {
        id: node.id,
        label: getNodeLabel(node),
        type: getNodeType(node),
        inputs: rectInputs,
        outputs: rectOutputs,
        tag: node.tag,
        ports: ports,
        inputIds
    };
    let optionalAttributes = {
        operation: node.apply,
        metadata: node.metadata,
        classMap: node.classMap,
        value: node.constant,
        positionOverride: node.metadata && node.metadata.positionOverride,
        analyses: new Set([analysis.id]),
        projId: node.projId,
        band: node.band
    };
    return Object.assign(nodeAttributes, _.omitBy(optionalAttributes, _.isUndefined));
}

export function createRect(node, dimensions) {
    return new joint.shapes.html.Element(Object.assign({
        id: node.id,
        size: {
            width: dimensions.width,
            height: dimensions.height
        },
        cellType: node.type,
        title: node.label || node.id.toString(),
        operation: node.operation,
        metadata: node.metadata,
        classMap: node.classMap,
        ports: {
            groups: {
                inputs: {
                    position: {
                        name: 'left'
                    }
                },
                outputs: {
                    position: {
                        name: 'right'
                    }
                }
            },
            items: node.ports
        }
    }, {
        value: node.value,
        positionOverride: node.positionOverride
    }));
}

export function analysisToNodesAndLinks(analysis) {
    let nodes = new Map();
    let linksBySource = new Map();
    let linksByTarget = new Map();
    let json = Object.assign({}, analysis.executionParameters);
    let parseStack = [json];

    while (parseStack.length) {
        let input = parseStack.pop();

        // Old ast's name 'projectSrc' input nodes as 'src'. New ast's use 'projectSrc'
        // In the future, we may want to write a migration to move them over.
        if (input.type === 'src') {
            input.type = 'projectSrc';
        }

        // Input nodes not of the layer type are not made into rectangles
        if (!input.type || input.type === 'projectSrc' || input.type === 'const') {
            let node;
            if (nodes.has(input.id)) {
                node = nodes.get(input.id);
            } else {
                node = getNodeAttributes(analysis, input);
                nodes = nodes.set(input.id, node);
            }

            if (input.parent) {
                const link = {source: node.id, target: input.parent.id};
                let targetsBySource = linksBySource.get(node.id) || new Map();
                linksBySource = linksBySource.set(
                    node.id,
                    targetsBySource ? targetsBySource.set(input.parent.id, link) :
                        new Map([[input.parent.id, link]])
                );
                let sourcesByTarget = linksByTarget.get(input.parent.id) || new Map();
                linksByTarget = linksByTarget.set(
                    input.parent.id,
                    sourcesByTarget ? sourcesByTarget.set(node.id, link) :
                        new Map([[node.id, link]])
                );
            }
            parseStack = parseStack.concat(
                getNodeArgs(input).reverse()
                    .map((a) => {
                        return Object.assign({
                            parent: node
                        }, a);
                    })
            );
        }
    }
    return {
        nodes, linksBySource, linksByTarget
    };
}

export function createNodeShapes(cellDimensions = {width: 400, height: 200}) {
    return (node) => {
        return Object.assign({}, node, {
            shape: createRect(node, cellDimensions)
        });
    };
}

export function addLinkShape(nodes, link) {
    let targetNode = nodes.get(link.target);
    let portId = targetNode.inputIds.findIndex((id) => id === link.source);
    if (portId < 0) {
        throw new Error(
            'Unable to find port when creating link between ' +
                `${link.source} -> ${link.target}.\n Node = `,
            targetNode
        );
    }
    let targetPort = `input-${portId}`;
    link.shape = new joint.dia.Link({
        source: {
            id: link.source,
            port: 'Output'
        },
        target: {
            id: link.target,
            port: targetPort
        },
        attrs: {
            '.marker-target': {
                d: 'M 4 0 L 0 2 L 4 4 z'
            },
            'g.link-ast': {
                display: 'none'
            },
            'g.marker-arrowheads': {
                display: 'none'
            },
            '.connection-wrap': {
                display: 'none'
            }
        }
    });
}

export function addLinkShapes(nodes, linkRefMap) {
    linkRefMap.forEach((linkMap) => {
        linkMap.forEach((link) => {
            addLinkShape(nodes, link);
        });
    });
}

export function analysesToNodeLinks(analyses, cellDimensions) {
    let analysesNodesAndLinks = analyses.map(
        (analysis) => analysisToNodesAndLinks(analysis)
    );
    let merged = analysesNodesAndLinks.reduce(
        ({nodes, linksBySource, linksByTarget},
         {nodes: aNodes, linksBySource: aLinksBySource, linksByTarget: aLinksByTarget}
        ) => {
            let mergedNodes = nodes.mergeWith((node, aNode) => {
                return Object.assign({}, node, {
                    analyses: node.analyses.union(aNode.analyses)
                });
            }, aNodes);
            const linkMapMerger = (links, aLinks) => links.merge(aLinks);
            let mergedLinksBySource = linksBySource.mergeWith(
                linkMapMerger, aLinksBySource
            );
            let mergedLinksByTarget = linksByTarget.mergeWith(
                linkMapMerger, aLinksByTarget
            );
            return {
                nodes: mergedNodes,
                linksBySource: mergedLinksBySource,
                linksByTarget: mergedLinksByTarget
            };
        }, {nodes: new Map(), linksBySource: new Map(), linksByTarget: new Map()}
    );

    let nodes = merged.nodes.map(createNodeShapes(cellDimensions));
    let {linksBySource, linksByTarget} = merged;
    addLinkShapes(nodes, merged.linksByTarget);
    return {
        nodes,
        linksBySource,
        linksByTarget
    };
}

export function setAnalysisRelativePositions(
    analysis, origin, onShapeMove,
    cellDimensions = {width: 400, height: 200}, nodeSeparationFactor = 0.2
) {
    if (!onShapeMove) {
        throw new Error('onShapeMove not defined in setAnalysisRelativePositions');
    }
    const layoutGraph = new joint.dia.Graph();
    const {
        nodes, linksBySource
    } = analysesToNodeLinks(
        [analysis],
        cellDimensions
    );

    nodes.forEach((node) => {
        layoutGraph.addCell(node.shape);
    });

    linksBySource.forEach(sourceLinks => {
        sourceLinks.forEach((link) => {
            layoutGraph.addCell(link.shape);
        });
    });

    const padding = cellDimensions.width * nodeSeparationFactor;

    joint.layout.DirectedGraph.layout(layoutGraph, {
        setLinkVertices: false,
        rankDir: 'LR',
        nodeSep: padding,
        rankSep: padding * 2
    });

    nodes.forEach(node => {
        const shape = node.shape;
        if (shape.attributes.position) {
            const position = {
                x: shape.attributes.position.x + origin.x,
                y: shape.attributes.position.y + origin.y
            };
            _.set(node, ['metadata', 'positionOverride'], position);
        }
    });

    return Object.assign({}, analysis, {
        executionParameters: astFromRootId(nodes, analysis.executionParameters.id)
    });
}

export function createLinkShapes(nodes) {
    return (link) => {
        let targetNode = nodes.get(link.target);
        let portId = targetNode.inputIds.findIndex((id) => id === link.source);
        let sourcePort = `input-${portId}`;

        return Object.assign({}, link, {
            shape: new joint.dia.Link({
                source: {
                    id: link.source,
                    port: 'Output'
                },
                target: {
                    id: link.target,
                    port: sourcePort
                },
                attrs: {
                    '.marker-target': {
                        d: 'M 4 0 L 0 2 L 4 4 z'
                    },
                    'g.link-ast': {
                        display: 'none'
                    },
                    'g.marker-arrowheads': {
                        display: 'none'
                    },
                    '.connection-wrap': {
                        display: 'none'
                    }
                }
            })
        });
    };
}

export function shapesFromElements(
    nodes = [], links = [], cellDimensions = {width: 400, height: 200}
) {
    let shapes = [];
    nodes.forEach((node) => {
        shapes.push(createRect(node, cellDimensions));
    });
    links.forEach((link) => {
        shapes.push(link.shape);
    });
    return shapes;
}

export default {
    astNodeFromNodeId,
    astFromRootId, astFromRootNode, getNodeArgs, nodesFromAnalysis, getNodeDefinition,
    getNodeHistogram, nodeIsChildOf, createPorts, getNodeLabel, getNodeType, getNodeAttributes,
    createRect, analysisToNodesAndLinks, createNodeShapes, addLinkShape, addLinkShapes,
    analysesToNodeLinks, setAnalysisRelativePositions, createLinkShapes,
    shapesFromElements,
    getNodeRoots, getNodeLeafs
};
