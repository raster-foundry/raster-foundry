import click
import geopandas as gpd

from utils import merge_labels_with_task_grid


@click.command()
@click.argument('hitl_project_id')
def run_hitl(hitl_project_id):
    pass

if __name__ == '__main__':
    run_hitl()
