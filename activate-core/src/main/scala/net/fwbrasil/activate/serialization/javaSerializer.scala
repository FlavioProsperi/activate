package net.fwbrasil.activate.serialization

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class JavaSerializerEvelope[T](val value: T) extends Serializable

object javaSerializer extends Serializer {

    def toSerialized[T: Manifest](value: T): Array[Byte] = {
        val envelope = new JavaSerializerEvelope(value)
        val baos = new ByteArrayOutputStream();
        val oos = new ObjectOutputStream(baos);
        oos.writeObject(envelope);
        baos.toByteArray
    }
    def fromSerialized[T: Manifest](bytes: Array[Byte]): T = {
        val bios = new ByteArrayInputStream(bytes);
        val ois = new ObjectInputStream(bios);
        val envelope = ois.readObject().asInstanceOf[JavaSerializerEvelope[T]];
        envelope.value.asInstanceOf[T]
    }
}