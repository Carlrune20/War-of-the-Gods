"""Just enough NBT to write and read MCEdit .schematic files: gzip-compressed, big-endian, one root compound."""

import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY = range(12)


class Tagged:
    """A value with an explicit tag type, for numbers whose type cannot be told from the Python value."""

    def __init__(self, tag, value):
        self.tag = tag
        self.value = value


def _tag_of(value):
    if isinstance(value, Tagged):
        return value.tag
    if isinstance(value, (bytes, bytearray)):
        return BYTE_ARRAY
    if isinstance(value, str):
        return STRING
    if isinstance(value, dict):
        return COMPOUND
    if isinstance(value, list):
        return LIST
    if isinstance(value, int):
        return INT
    raise TypeError("no NBT tag for %r" % (value,))


def _write(out, tag, value):
    if isinstance(value, Tagged):
        value = value.value
    if tag == BYTE:
        out.write(struct.pack(">b", value))
    elif tag == SHORT:
        out.write(struct.pack(">h", value))
    elif tag == INT:
        out.write(struct.pack(">i", value))
    elif tag == LONG:
        out.write(struct.pack(">q", value))
    elif tag == FLOAT:
        out.write(struct.pack(">f", value))
    elif tag == DOUBLE:
        out.write(struct.pack(">d", value))
    elif tag == BYTE_ARRAY:
        out.write(struct.pack(">i", len(value)))
        out.write(bytes(value))
    elif tag == STRING:
        data = value.encode("utf-8")
        out.write(struct.pack(">H", len(data)))
        out.write(data)
    elif tag == LIST:
        element = _tag_of(value[0]) if value else COMPOUND
        out.write(struct.pack(">bi", element, len(value)))
        for item in value:
            _write(out, element, item)
    elif tag == COMPOUND:
        for name, item in value.items():
            item_tag = _tag_of(item)
            out.write(struct.pack(">b", item_tag))
            _write(out, STRING, name)
            _write(out, item_tag, item)
        out.write(struct.pack(">b", END))
    elif tag == INT_ARRAY:
        out.write(struct.pack(">i", len(value)))
        out.write(struct.pack(">%di" % len(value), *value))
    else:
        raise ValueError(tag)


def write_compressed(path, root, name="Schematic"):
    out = io.BytesIO()
    out.write(struct.pack(">b", COMPOUND))
    _write(out, STRING, name)
    _write(out, COMPOUND, root)
    # A fixed mtime keeps the files byte for byte the same when nothing changed.
    with open(path, "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as gz:
            gz.write(out.getvalue())


def _read(f, tag):
    if tag == BYTE:
        return struct.unpack(">b", f.read(1))[0]
    if tag == SHORT:
        return struct.unpack(">h", f.read(2))[0]
    if tag == INT:
        return struct.unpack(">i", f.read(4))[0]
    if tag == LONG:
        return struct.unpack(">q", f.read(8))[0]
    if tag == FLOAT:
        return struct.unpack(">f", f.read(4))[0]
    if tag == DOUBLE:
        return struct.unpack(">d", f.read(8))[0]
    if tag == BYTE_ARRAY:
        return f.read(struct.unpack(">i", f.read(4))[0])
    if tag == STRING:
        return f.read(struct.unpack(">H", f.read(2))[0]).decode("utf-8")
    if tag == LIST:
        element, count = struct.unpack(">bi", f.read(5))
        return [_read(f, element) for _ in range(count)]
    if tag == COMPOUND:
        result = {}
        while True:
            item_tag = f.read(1)[0]
            if item_tag == END:
                return result
            name = _read(f, STRING)
            result[name] = _read(f, item_tag)
    if tag == INT_ARRAY:
        count = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(">%di" % count, f.read(4 * count)))
    raise ValueError(tag)


def read_compressed(path):
    with gzip.open(path, "rb") as gz:
        f = io.BytesIO(gz.read())
    tag = f.read(1)[0]
    _read(f, STRING)
    return _read(f, tag)
