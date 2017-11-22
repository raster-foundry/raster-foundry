export function propertiesToAnnotationFeature({
    geometry, label = '', description = '', organizationId
}) {
    return {
        type: 'Feature',
        geometry,
        properties: {
            label, organizationId, description
        }
    };
}

export function wrapFeatureCollection(annotation) {
    return {
        type: 'FeatureCollection',
        features: [annotation]
    };
}

export function annotationsToFeatureCollection(annotations) {
    let annotationsArray = annotations;
    if (annotations.toArray) {
        annotationsArray = annotations.toArray();
    }
    return {
        type: 'FeatureCollection',
        features: annotationsArray.map(annotation => {
            let annotationWithoutId = Object.assign({}, annotation);
            delete annotationWithoutId.id;
            return annotationWithoutId;
        })
    };
}
