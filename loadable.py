import os.path
from json import dumps, loads
from os.path import exists
import pathlib

from json_objects import convert_to_dict


class Loadable:
    def __init__(self, path, version):
        self._path = path
        self.version = version

    def _store(self) -> None:
        pathlib.Path(os.path.dirname(self._path)).mkdir(parents=True, exist_ok=True)

        with open(self._path, "w", encoding="utf-8-sig") as outfile:
            outfile.write(dumps(self, default=convert_to_dict, indent=4))

    def _load(self) -> None:
        if not exists(self._path):
            self._store()
            return
        try:
            with open(self._path, encoding="utf-8-sig") as json_file:
                loaded = loads(json_file.read())

                if loaded["version"] == self.version:
                    for attr in loaded.keys():
                        if not attr.startswith("__") and hasattr(self, attr):
                            setattr(self, attr, loaded[attr])
                else:
                    self._migrate(loaded)
        except Exception:
            print("State could not be loaded .. reinitialize")
            self._store()

    def _migrate(self, loaded):
        pass
