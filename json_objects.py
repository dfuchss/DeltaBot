# These functions are accessible via GIST:
# https://gist.github.com/ardenn/76aa5653245388519a2edb690d8ed7ba
# https://gist.github.com/ardenn/30f94f57876a70832a5c960fd4742d89


def convert_to_dict(obj: object) -> dict:
    """
    A function takes in a custom object and returns a dictionary representation of the object.
    This dict representation includes meta data such as the object's module and class names.

    :param obj the object to be serialized
    :return the object as dictionary
    """

    #  Populate the dictionary with object meta data
    obj_dict = {
        "__class__": obj.__class__.__name__,
        "__module__": obj.__module__
    }

    #  Populate the dictionary with object properties
    obj_dict.update(obj.__dict__)

    return obj_dict


def dict_to_obj(dictionary: dict) -> object:
    """
    Function that takes in a dict and returns a custom object associated with the dict.
    This function makes use of the "__module__" and "__class__" metadata in the dictionary
    to know which object type to create.

    :param dictionary the dictionary which represents the object
    :return the object 
    """
    if "__class__" in dictionary:
        # Pop ensures we remove metadata from the dict to leave only the instance arguments
        class_name = dictionary.pop("__class__")

        # Get the module name from the dict and import it
        module_name = dictionary.pop("__module__")

        # We use the built in __import__ function since the module name is not yet known at runtime
        module = __import__(module_name)

        # Get the class from the module
        class_ = getattr(module, class_name)

        # Use dictionary unpacking to initialize the object
        obj = class_(**dictionary)
    else:
        obj = dictionary
    return obj
