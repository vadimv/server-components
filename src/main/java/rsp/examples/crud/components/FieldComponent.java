package rsp.examples.crud.components;

import rsp.Component;
import rsp.examples.crud.entities.Keyed;
import rsp.examples.crud.entities.KeyedEntity;

import java.util.Optional;


public interface FieldComponent<S> extends Component<S>, Keyed<String> {

}
