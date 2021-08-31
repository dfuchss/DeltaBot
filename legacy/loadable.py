import os.path
import pathlib
from json import dumps, loads
from os.path import exists
from typing import List

from json_objects import convert_to_dict


class Loadable:
    """Defines a loadable / storable class"""

    def __init__(self, path: str, version: int):
        self._path = path
        """The path to the loadable file"""

        self.version = version
        """The version of the loadable"""

    def _store(self) -> None:
        """Store the loadable to the specified file"""

        pathlib.Path(os.path.dirname(self._path)).mkdir(parents=True, exist_ok=True)

        with open(self._path, "w", encoding="utf-8-sig") as outfile:
            outfile.write(dumps(self, default=convert_to_dict, indent=4))

    def _load(self) -> None:
        """Load the loadable from the specified file"""

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
                    # Store new properties (and delete old ones)
                    self._store()
                else:
                    self._migrate(loaded)
        except Exception:
            print("State could not be loaded .. reinitialize")
            self._store()

    def _migrate(self, loaded: dict):
        """
        Will be invoked iff versions of loaded file and this loadable differ.

        :param loaded: the loaded data from the file (needs manually applied to the loadable)
        """
        pass


class DictStore(Loadable):
    """
    This state contains all future reminders.
    """

    def __init__(self, path):
        super().__init__(path=path, version=1)
        self._data = []
        self._load()

    def add_data(self, data: dict) -> None:
        """
        Add a new data point.

        :param data: the data point as dictionary
        """
        self._data.append(data)
        self._store()

    def remove_data(self, data: dict) -> None:
        """
        Remove a data point.

        :param data: the data as dictionary
        """
        if data in self._data:
            self._data.remove(data)
            self._store()

    def data(self) -> List[dict]:
        """
        Get all stored data points.

        :return: a list of data points as dictionaries
        """
        return self._data
