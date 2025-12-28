package com.leikooo.codemother.commen;

import lombok.Data;

import java.io.Serializable;

/**
 * @author leikooo
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
