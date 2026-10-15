"""Lossless numeric terrain prediction and signed variable-length residuals."""
from array import array

HEIGHT_MASK = 65535
VARINT_BITS = 7
VARINT_CONTINUATION = 128


def _prediction(left, up, diagonal, predictor):
    if predictor == "gradient":
        return left + up - diagonal
    if predictor == "left":
        return left
    if predictor == "median":
        if diagonal >= max(left, up):
            return min(left, up)
        if diagonal <= min(left, up):
            return max(left, up)
        return left + up - diagonal
    raise ValueError(f"Unsupported numeric terrain predictor: {predictor}")


def encode_numeric(values, width, predictor):
    output = bytearray()
    for index, value in enumerate(values):
        column = index % width
        left = values[index - 1] if column else 0
        up = values[index - width] if index >= width else 0
        diagonal = values[index - width - 1] if index >= width and column else 0
        residual = value - _prediction(left, up, diagonal, predictor)
        encoded = residual * 2 if residual >= 0 else -residual * 2 - 1
        while encoded >= VARINT_CONTINUATION:
            output.append((encoded & (VARINT_CONTINUATION - 1)) | VARINT_CONTINUATION)
            encoded >>= VARINT_BITS
        output.append(encoded)
    return bytes(output)


def decode_numeric(data, width, predictor):
    values = array("H")
    position = 0
    for index in range(width * width):
        encoded = shift = 0
        while True:
            if position >= len(data):
                raise ValueError("Truncated numeric terrain residuals")
            byte = data[position]
            position += 1
            encoded |= (byte & (VARINT_CONTINUATION - 1)) << shift
            if byte < VARINT_CONTINUATION:
                break
            shift += VARINT_BITS
            if shift > VARINT_BITS * 3:
                raise ValueError("Oversized numeric terrain residual")
        residual = -(encoded // 2) - 1 if encoded & 1 else encoded // 2
        column = index % width
        left = values[index - 1] if column else 0
        up = values[index - width] if index >= width else 0
        diagonal = values[index - width - 1] if index >= width and column else 0
        value = _prediction(left, up, diagonal, predictor) + residual
        if not 0 <= value <= HEIGHT_MASK:
            raise ValueError("Invalid decoded numeric terrain height")
        values.append(value)
    if position != len(data):
        raise ValueError("Trailing numeric terrain residuals")
    return values
