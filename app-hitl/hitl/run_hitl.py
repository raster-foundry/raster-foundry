import click
import geopandas as gpd

from utils import merge_labels_with_task_grid


@click.command()
@click.argument('hitl_project_id')
def run_hitl(hitl_project_id):
    print('PLACEHOLDER:')
    print(f'Processing HITL project "{hitl_project_id}"')
    tasks = gpd.read_file('/opt/src/data/hitl/hitl-tasks.json')
    labels = gpd.read_file('/opt/src/data/hitl/hitl-predictions.geojson')
    merged_labels = merge_labels_with_task_grid(labels, tasks)
    print('merged labels (head):')
    print(merged_labels.head())
    print(
        f'{labels.shape[0]} labels before intersection, {merged_labels.shape[0]} labels after')


if __name__ == '__main__':
    run_hitl()
